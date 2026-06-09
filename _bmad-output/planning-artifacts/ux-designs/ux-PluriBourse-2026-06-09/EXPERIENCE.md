---
name: PluriBourse
status: final
updated: 2026-06-09
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

## Information Architecture

### Admin

| Surface | Chemin | Accessible en |
|---|---|---|
| Éditions — liste | `/admin/editions` | Toutes phases |
| Édition — détail / création | `/admin/editions/:id` | Toutes phases |
| Édition — Catégories & Tables | `/admin/editions/:id/categories` | Avant phase Dépôt (éditable) · Après : lecture seule |
| Édition — Contrôle de phase | `/admin/editions/:id/phase` | Toutes phases |
| Vendeurs — liste | `/admin/sellers` | Toutes phases |
| Vendeur — fiche / création | `/admin/sellers/:id` | Toutes phases |
| Articles — catalogue | `/admin/catalog` | Toutes phases |
| Rapports | `/admin/reports` | Vente (journalier) · Post-vente · Clôturée |
| Utilisateurs | `/admin/users` | Toutes phases |
| Paramètres instance | `/admin/settings` | Toutes phases |

### Bénévole (interface adaptée à la phase active)

| Phase active | Surface | Chemin |
|---|---|---|
| Dépôt | Accueil dépôt | `/volunteer/deposit` |
| Vente | Caisse | `/volunteer/pos` |
| Post-vente | Reversements | `/volunteer/settlement` |
| Toutes | Catalogue | `/volunteer/catalog` |

### Partagées

| Surface | Chemin |
|---|---|
| Connexion | `/login` |
| Mon compte | `/account` |

### Navigation Admin — sidebar

```
Édition active
  ├── Vendeurs          (icône: people)
  └── Articles          (icône: inventory_2)
Gestion
  ├── Éditions          (icône: event)
  ├── Rapports          (icône: assessment)
  ├── Utilisateurs      (icône: manage_accounts)
  └── Paramètres        (icône: settings)
```

L'entrée active dans la sidebar correspond à la route courante. Aucun sous-menu dépliable — la navigation reste plate et lisible.

→ Composition référence : `.working/navigation-layouts.html` (Option 3). Spine wins on conflict.
→ Maquette layout Admin : `mockups/mock-admin-vendors.html`
→ Maquette caisse POS (lot incomplet) : `mockups/mock-pos-caisse.html`
→ Maquette caisse POS (lot complet) : `mockups/mock-pos-caisse-lot-complet.html`
→ Maquette formulaire dépôt : `mockups/mock-deposit.html`
→ Maquette contrôle de phase : `mockups/mock-phase-control.html`

## Voice and Tone

Langue de l'interface : EN ou FR selon la préférence du compte utilisateur (ngx-translate). Les règles ci-dessous s'appliquent aux deux langues.

**En français : vouvoiement systématique.** L'application s'adresse à des bénévoles de tous âges ; le vouvoiement est neutre et inclusif.

| Do | Don't |
|---|---|
| "Êtes-vous sûr de vouloir passer en phase Vente ?" | "T'es sûr ?" · "Confirmer ?" sans contexte |
| "Aucun vendeur trouvé. Créez un nouveau profil." | "Pas de résultat." |
| "Article déjà vendu sur un autre poste." | "Erreur 409." · "Conflit détecté." |
| "Lot incomplet — il manque 2 articles sur 4." | "Lot invalide." |
| "L'imprimante ne répond pas. Vérifiez la connexion USB." | "Erreur d'impression." |
| "Vendeur réglé." (toast succès) | "Opération effectuée avec succès." |
| Actions : verbe + objet — "Valider le dépôt", "Régler le vendeur" | Jargon technique visible |
| Labels : noms — "Catégorie", "Table", "Reversement" | Abréviations non évidentes |

**Confirmations de phase** : toujours nommer la phase de destination et décrire la conséquence principale. Exemple : "Passer en phase Vente. Les dépôts ne seront plus modifiables."

**États vides** : toujours proposer une action. Jamais un état vide sans sortie.

## Component Patterns

Comportemental. Spécifications visuelles dans `DESIGN.md.Components`.

| Composant | Usage | Règles comportementales |
|---|---|---|
| **Topbar** | Global | Logo à gauche · Phase chip au centre · Role badge + icône profil à droite. Phase chip cliquable Admin (→ contrôle de phase). Non cliquable Bénévole. |
| **Sidebar** | Admin uniquement | Largeur fixe 200px. Non collapsable en v1. Entrée active déterminée par la route courante. |
| **Phase chip** | Topbar | Mis à jour en temps réel via SSE (`phase-changed`). Transition animée : fade 150ms. |
| **Dialog de confirmation** | Transitions de phase, actions destructives, nettoyage d'édition | Bloque l'interaction derrière un overlay. Toujours : titre + conséquence + bouton confirm + bouton annuler. Focus piégé dans le dialog (accessibilité). Fermeture : bouton annuler ou Echap. |
| **Notification inline** | Erreurs métier dans le flux (POS, dépôt) | Apparaît directement sous l'élément déclencheur, pas en toast. Reste visible jusqu'à résolution ou nouvelle action. Icône `warning` + message en langage naturel. |
| **Toast** | Confirmations de succès, erreurs système | Bottom-right. Succès : 4s puis disparaît. Erreur système (imprimante) : persistant, bouton "Fermer". Max 1 toast simultané. |
| **Catalogue / liste filtrée** | Admin + Bénévole | Filtres en ligne au-dessus de la liste. Tri par clic sur le header de colonne (↑↓). Pagination via `MatPaginator` — page size par défaut 50. |
| **Scanner input** | Caisse (POS) | Champ auto-focused à l'ouverture de la caisse. Capture les événements keyboard du scanner USB HID. Traitement à la touche `Enter` (ou `\n` selon scanner). AZERTY/QWERTY géré par key code mapping côté Angular. Pas de debounce — traitement immédiat. |
| **Panier POS** | Caisse | Liste des articles scannés avec prix unitaire et total. Suppression individuelle par icône `close` sur chaque ligne. Bouton "Valider" bloqué si lot incomplet. Annulé automatiquement si changement de phase (SSE `basket-cancelled`). |
| **Lot dans le panier** | Caisse | Articles du lot affichés groupés avec label du lot en rouge. Compteur "X/N scannés". Sous-total du lot affiché dans l'en-tête du groupe (somme des articles scannés). **Pas de prix individuel par article** — le lot est une unité de vente. Bouton "Retirer le lot entier" visible dès le premier article du lot dans le panier. Validation bloquée tant que lot incomplet. |
| **Formulaire dépôt** | Bénévole — phase Dépôt | Recherche vendeur par nom ou email en premier. Si non trouvé : bouton "Créer un profil". Saisie article : nom, prix, catégorie (sélecteur), complet/incomplet. Table assignée automatiquement. |
| **Fiche Catégories & Tables** | Admin — édition | Mode édition jusqu'au démarrage de la phase Dépôt. Mode lecture après. À la création : option "Copier depuis une édition existante" (sélecteur d'édition) ou "Configurer manuellement". |
| **Page Rapports** | Admin — phases Vente · Post-vente · Clôturée | Contenu conditionnel selon la phase active — seules les sections pertinentes à la phase courante sont affichées, les autres sont absentes. **Rapport de caisse journalier** (Vente uniquement) : montant total des ventes du jour, bouton "Actualiser". **Rapport de synthèse** (Post-vente + Clôturée) : total ventes, total reversements, total recettes association — lecture seule. **Exports CSV** (Post-vente + Clôturée) : boutons "Exporter le catalogue" (articles + statut vendu/non vendu) et "Exporter les reversements" — téléchargement direct sans dialog. **Liste vendeurs non réglés imprimable** (Post-vente) : bouton "Imprimer la liste" — ouvre la vue impression du navigateur. |
| **Action "Nettoyer l'édition"** | Admin — phase Clôturée | Bouton visible sur la fiche édition uniquement si des articles non supprimés existent. Style `secondary` couleur `error`. Dialog de confirmation : "Supprimer tous les articles de cette édition. Cette action est irréversible." Boutons : "Supprimer" (error) + "Annuler" (ghost). Post-Clean : le catalogue affiche un état vide "Édition nettoyée — aucun article." sans action proposée. Le bouton "Nettoyer l'édition" disparaît définitivement. |
| **Récapitulatif reversement imprimable** | Bénévole — phase Post-vente · Admin toutes phases | Bouton "Imprimer le récapitulatif" accessible depuis la ligne vendeur sur `/volunteer/settlement` (après règlement) et depuis la fiche vendeur Admin. Feedback : spinner dans le bouton pendant la mise en queue. Résultat communiqué par toast succès (4s) ou toast persistant si imprimante hors ligne. Toujours rejouable. |

## State Patterns

| État | Surface | Traitement |
|---|---|---|
| Chargement initial | Listes, catalogues | Skeleton rows Angular Material (3–5 lignes). Pas de spinner global. |
| Liste vide | Vendeurs, articles, reversements | Icône Material Symbols centré + phrase descriptive + action primaire. Ex : "Aucun vendeur enregistré. Créez le premier profil." |
| Résultats filtrés vides | Catalogue, liste vendeurs | "Aucun résultat pour ces filtres." + bouton "Effacer les filtres". Pas d'état vide générique. |
| Erreur de chargement | Toutes | Card d'erreur avec message naturel + bouton "Réessayer". |
| Sauvegarde en cours | Formulaires | Bouton de validation désactivé + spinner inline dans le bouton. Pas de loader plein écran. |
| Conflit POS (article déjà vendu) | Caisse | Notification inline rouge sous le scanner + liste des articles en conflit. Bénévole retire les articles manuellement. Pas de résolution automatique. |
| Lot incomplet | Caisse | Notification inline orange dans le panier. Bouton "Valider" bloqué. Compteur X/N mis à jour à chaque scan. |
| Changement de phase pendant une transaction | Caisse | Toast persistant : "La phase a changé. Votre panier a été annulé." Panier vidé. Scanner désactivé jusqu'à rechargement de la page caisse. |
| Imprimante hors ligne | Toast | Toast persistant : "L'imprimante [thermique / A4] ne répond pas. Vérifiez la connexion USB." Bouton "Fermer". L'action d'impression reste rejouable depuis l'interface. |
| Session expirée | Global | Redirection vers `/login` avec message "Votre session a expiré. Reconnectez-vous." (théoriquement impossible vu FR-066, mais couvert par sécurité). |
| Phase Clôturée — édition archivée | Admin | Données en lecture seule. Bannière "Édition clôturée" sous la topbar. Bouton "Nettoyer l'édition" accessible si articles non supprimés. |

## Interaction Primitives

**Scanner USB HID — caisse uniquement**
La caisse est conçue pour une utilisation scanner-first. Le champ de scan est autofocused et capte tous les événements clavier. Un clic involontaire sur la page ne doit pas perdre le focus du scanner — le composant `scanner.component.ts` remet le focus automatiquement après 500ms d'inactivité clavier.

**Clavier — navigation générale**
- `Tab` / `Shift+Tab` : navigation entre éléments interactifs dans l'ordre visuel
- `Enter` / `Space` : activation des boutons et liens
- `Echap` : fermeture des dialogs et popovers
- Pas de raccourcis vim ou globaux en v1 — l'audience bénévole ne les utiliserait pas

**Formulaires**
- Validation côté client : Angular reactive forms, feedback immédiat sur `blur`
- Validation côté serveur : erreurs mappées sur les champs concernés via RFC 7807
- `Enter` dans un formulaire mono-champ (recherche vendeur) soumet le formulaire
- `Enter` dans un formulaire multi-champs déplace le focus au champ suivant

**Actions irréversibles**
Toute action irréversible ou à fort impact (transition de phase, suppression profil vendeur, Clean Edition) nécessite un dialog de confirmation avec description des conséquences. Aucune action de ce type n'est accessible depuis un double-clic ou un raccourci clavier non-intentionnel.

**Impression**
Déclenchée par un bouton explicite dans l'interface. Feedback immédiat : spinner dans le bouton pendant la mise en queue. Résultat communiqué par toast (succès) ou toast persistant (erreur). Le bouton redevient actif après traitement — l'impression est toujours rejouable.

**Interdit**
- Drag-and-drop (v1)
- Infinite scroll (utiliser pagination ou chargement complet pour les volumes PluriBourse)
- Hover-only affordances (pas d'action visible uniquement au survol)
- Double-clic comme déclencheur d'action primaire
- Auto-submit de formulaire sans confirmation utilisateur

## Accessibility Floor

Comportemental. Contrastes visuels dans `DESIGN.md`.

- **WCAG 2.2 AA minimum** sur toute la surface. Couleur primaire `#C44626` sur fond blanc : ratio 4.6:1 (AA ✓). Version sombre `#F07040` sur `#1A0C06` : ratio 5.2:1 (AA ✓).
- **Focus ring** visible sur tous les éléments interactifs — hérité du token `{colors.primary}` Angular Material, jamais supprimé.
- **Tab order** suit l'ordre de lecture visuel sur chaque page. Sidebar → contenu principal → topbar actions (ordre DOM correspondant).
- **Focus trap** dans les dialogs de confirmation — `Tab` ne sort pas du dialog tant qu'il est ouvert. Focus initial sur le bouton d'annulation (action sûre).
- **Annonces screen reader** : titre de page annoncé à chaque navigation (`<title>` mis à jour). Listes annoncées avec leur nombre d'éléments via `aria-label`. Erreurs de formulaire annoncées via `aria-describedby`.
- **Phase chip** porte un `aria-label` complet : "Phase actuelle : Dépôt" (pas seulement le texte visible).
- **Scanner input** : `aria-label="Scanner ou saisir un code-barres"` · `aria-live="polite"` sur la zone de résultat du scan pour annoncer l'article ajouté au panier.
- **Taille minimale des cibles tactiles** : 44×44px (même en desktop — bénévoles potentiellement moins précis avec la souris sous stress d'événement).
- Icônes décoratives : `aria-hidden="true"`. Icônes porteuses de sens : accompagnées d'un label texte ou `aria-label`.

## Key Flows

### Flow 1 — Dépôt d'un vendeur (Sophie, bénévole accueil, phase Dépôt)

1. Sophie arrive sur `/volunteer/deposit`. Le champ de recherche vendeur reçoit le focus à l'ouverture.
2. Elle tape le nom "Martin" dans la recherche vendeur — résultats filtrés en temps réel.
3. Elle sélectionne "Martin Pierre". La fiche vendeur s'ouvre avec la liste de ses articles des éditions précédentes en lecture seule.
4. Elle saisit les articles un par un : nom, prix, catégorie. La table est assignée automatiquement.
5. Pour un article de jeu de société incomplet, elle coche "Incomplet" et saisit le détail manquant.
6. **Climax :** Elle clique "Valider le dépôt". Un spinner apparaît dans le bouton. 2 secondes plus tard : toast "Dépôt enregistré." L'imprimante thermique démarre automatiquement — séparateur vendeur puis étiquettes. Sophie passe au suivant.

Échec imprimante : toast persistant "L'imprimante ne répond pas." Le dépôt est enregistré ; les étiquettes sont rejouables depuis la fiche vendeur.

### Flow 2 — Vente avec lot incomplet (Marc, bénévole caisse, phase Vente)

1. Marc est sur `/volunteer/pos`. Scanner autofocused, panier vide.
2. Il scanne plusieurs articles normaux — ajoutés au panier avec nom et prix.
3. Il scanne un article appartenant à un lot "Jeu de société Catan". Notification inline orange : "Lot Catan — 1/3 scannés." Bouton "Valider" grisé.
4. Il scanne le deuxième article du lot. Compteur : "2/3".
5. **Climax :** L'acheteur ne retrouve pas le 3ème article. Marc clique "Retirer le lot entier". Les 2 articles du lot disparaissent du panier. Le bouton "Valider" se débloque. La transaction continue avec les articles hors lot.

### Flow 3 — Reversement, vendeur absent (Sophie, phase Post-vente)

1. Sophie ouvre `/volunteer/settlement`. Liste des vendeurs non réglés avec montant dû et téléphone visible.
2. Elle appelle Martin Pierre — pas de réponse. Elle clique "Non réclamé" sur sa ligne.
3. Dialog de confirmation : "Le montant de 34,50 € sera versé aux recettes de l'association. Cette action est irréversible." Deux boutons : "Confirmer" (primary) et "Annuler" (ghost).
4. **Climax :** Elle confirme. La ligne disparaît de la liste des non réglés. Toast : "Montant transféré aux recettes." Sophie passe au vendeur suivant.

### Flow 4 — Transition de phase (Laurent, admin)

1. Laurent est sur `/admin/editions/1/phase`. Il voit la phase actuelle "Dépôt" et le bouton "Passer en phase Vente".
2. Il clique. Dialog de confirmation : "Passer en phase Vente. Les dépôts ne seront plus modifiables. Les caisses bénévoles s'activeront." Boutons : "Confirmer" et "Annuler".
3. **Climax :** Il confirme. La phase chip dans la topbar passe de "Dépôt" à "Vente" (fade 150ms). Sur tous les postes bénévoles connectés, la même transition s'affiche via SSE — l'interface bascule automatiquement sur la caisse. Un bénévole qui avait un formulaire de dépôt ouvert voit un toast : "La phase a changé. Votre session de dépôt a été annulée."
