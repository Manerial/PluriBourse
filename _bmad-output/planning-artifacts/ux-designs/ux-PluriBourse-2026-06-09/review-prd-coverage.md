---
title: "PRD Coverage Review — PluriBourse"
reviewer: UX requirements coverage audit
reviewed: 2026-06-15
sources:
  - prds/prd-PluriBourse-2026-06-08/prd.md
  - ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md
---

# PRD Coverage Review — PluriBourse

## Verdict global

EXPERIENCE.md (version 2026-06-15) couvre solidement les flux opérationnels principaux et les composants critiques : caisse POS, dépôt, reversements, cycle de vie des éditions, imprimantes. Sur les 89 FRs nécessitant un traitement UX (NFRs exclus), 61 sont explicitement couverts, 14 couverts implicitement, 3 non couverts, et 13 relèvent d'exigences purement techniques (N/A). Le taux de couverture explicite est de 76 % (61/81), 91 % en incluant les implicites. Les lacunes les plus critiques concernent la modification de l'indicateur complet/incomplet hors phase Dépôt (FR-025), le filtre "nom du vendeur" absent du composant Catalogue (FR-084), et le contenu de l'étiquette thermique pour les lots (FR-045).

---

## FRs non couverts

| FR | Libellé court | Sévérité | Fix suggéré |
|---|---|---|---|
| FR-025 | Indicateur complet/incomplet et commentaire modifiables dans toutes les phases | **high** | Dans le composant "Catalogue / liste filtrée", spécifier que les champs complet/incomplet et commentaire sont éditables inline (ou via dialog) en phases Vente, Post-vente et Clôturée — accessible depuis la colonne actions de la `list-row` par un bouton "Modifier". |
| FR-030 | Format du rouleau thermique : séparateur vendeur → étiquette → séparateur article → étiquette → … | **medium** | Ajouter dans l'Interaction Primitive "Impression" une note sur la structure séquentielle du rouleau par vendeur. Sans cela, la structure est laissée à l'interprétation du développeur backend. |
| FR-045 | Contenu de l'étiquette lot : "Prix du lot : X€" + "Lot indivisible : X/N" à la place du prix individuel | **medium** | Dans le composant "Formulaire lot", ajouter un tableau comparatif "Étiquette standard vs Étiquette lot" listant les champs différenciants (pas de prix individuel, mention Prix du lot, position X/N). |

---

## FRs couverts implicitement (signal d'alerte)

| FR | Couverture actuelle | Risque |
|---|---|---|
| FR-004 | ngx-translate mentionné dans Voice and Tone, aucune règle UX explicite sur l'interdiction de hardcoded strings dans les composants dynamiques | Risque faible — couvert par CLAUDE.md, mais messages d'état vide et toasts peuvent être oubliés |
| FR-010 | Unicité de l'édition active implicite dans l'IA — aucune règle UX sur le comportement du bouton "Créer une édition" si une édition active existe déjà | Un dev pourrait autoriser deux éditions actives simultanées faute de contrainte UX |
| FR-014 | Blocage de la suppression d'édition post-Préparation non spécifié dans l'UX — ni bouton absent, ni tooltip, ni message | Si un bouton "Supprimer" existe sur la liste des éditions, son état en phase Dépôt+ est indéfini |
| FR-019 | Champs obligatoires du profil vendeur (nom, prénom, email, téléphone) et leurs validations absents de la spec du formulaire de dépôt | Validations email/téléphone non implémentées ou incohérentes selon les développeurs |
| FR-024 | Suppression d'article mentionnée dans la note du catalogue admin ("phase Dépôt uniquement") sans spec du CTA, de la confirmation, ni du post-état | Danger zone ? Dialog de confirmation ? Comportement non défini |
| FR-027 | Layout visuel de l'étiquette standard (ordre des champs, centrage, mention INCOMPLET, numéro de table, barcode XXXX-XXXX, absence du nom vendeur RGPD) décrit dans le PRD uniquement | Risque d'implémentation incohérente avec le PRD sur le rendu visuel de l'étiquette |
| FR-041 | Contenu de la facture acheteur (liste articles, total, nom association, nom édition, date ; lot sur une ligne) non spécifié dans EXPERIENCE.md — seul le bouton "Imprimer la facture" est décrit | Structure du PDF laissée à l'interprétation du développeur |
| FR-050 | Contenu du bilan de vente (articles vendus, invendus + table, total brut, commission, net ; lot sur une ligne) décrit dans Flow 5 narrativement mais sans spec de composant formelle | Divergence possible avec le PRD sur la structure du document |
| FR-055 | Génération automatique du bilan d'édition PDF (deux langues EN+FR) au moment de la clôture non décrite dans le flow de clôture — ni spinner, ni toast "Rapport généré" | Le déclenchement automatique et son retour visuel sont absents du composant "Contrôle de phase" |
| FR-061 | Un seul compte admin par instance — aucune contrainte UX sur le sélecteur de rôle à la création d'un compte (rôle "Admin" proposable ?) | Possible création d'un second admin si le sélecteur n'est pas contraint |
| FR-063 | Réinitialisation MDP admin via CLI non explicitement mappée au flow de forced-password dans EXPERIENCE.md | Un dev pourrait ne pas traiter le MDP temporaire CLI comme un forced-password au login |
| FR-084 | Le composant "Catalogue / liste filtrée" liste les filtres sans mention du filtre "nom du vendeur" requis par FR-084 | Filtre absent de l'implémentation Angular |

> Note : FR-050 et FR-055 étaient référencés comme PASS dans la revue précédente (2026-06-12) mais restent implicites — le contenu documentaire exact des PDF n'est spécifié que dans le PRD, pas dans EXPERIENCE.md.

---

## Couverture complète par groupe fonctionnel

| Groupe | Total FRs | Couverts (✓) | Implicites (~) | Non couverts (✗) | N/A |
|---|---|---|---|---|---|
| F1 — Internationalisation | 7 | 5 | 1 | 0 | 1 |
| F2 — Éditions & Cycle de vie | 11 | 9 | 1 | 0 | 1 |
| F3 — Vendeurs & Articles | 18 | 9 | 4 | 3 | 2 |
| F4 — Point de Vente | 13 | 11 | 1 | 0 | 1 |
| F5 — Post-Vente | 7 | 5 | 2 | 0 | 0 |
| F6 — Rapports | 8 | 7 | 1 | 0 | 2 |
| F7 — Comptes Utilisateurs | 8 | 5 | 3 | 0 | 0 |
| F8 — Infrastructure (UI uniquement) | 8 | 2 | 0 | 0 | 6 |
| F9 — Impression | 5 | 5 | 0 | 0 | 0 |
| F10 — Catalogue | 4 | 3 | 1 | 0 | 0 |
| **Total** | **89** | **61** | **14** | **3** | **13** |

> NFRs (NFR-001 à NFR-007) exclus du tableau — exigences non fonctionnelles sans spec UX dédiée requise.

---

## Findings

- **[high]** FR-025 — Aucun chemin UX pour modifier l'indicateur complet/incomplet et le commentaire hors phase Dépôt (Vente, Post-vente, Clôturée). Le composant "Catalogue / liste filtrée" ne spécifie aucune action d'édition partielle d'article. *Fix :* Ajouter dans le composant Catalogue une colonne d'actions avec un bouton "Modifier statut" (accessible en toutes phases), ouvrant un dialog ou un inline edit limité aux champs complet/incomplet et commentaire.

- **[high]** FR-084 — Le filtre "nom du vendeur" est requis par le PRD pour le catalogue mais absent de la liste des filtres dans le composant "Catalogue / liste filtrée". *Fix :* Ajouter "nom du vendeur" dans la liste des filtres du composant Catalogue (section Component Patterns).

- **[medium]** FR-045 — L'étiquette thermique d'un article de lot doit afficher "Prix du lot : X€" et "Lot indivisible : X/N" à la place d'un prix individuel. Aucune spec visuelle dans EXPERIENCE.md (ni dans le composant Formulaire lot, ni dans la section Impression). *Fix :* Documenter un tableau comparatif étiquette standard / étiquette lot dans le composant "Formulaire lot".

- **[medium]** FR-030 — La structure séquentielle du rouleau thermique (séparateur vendeur + séquence étiquettes entrecoupées de séparateurs article) n'est documentée que dans le PRD. Elle conditionne la lisibilité en dépôt (bénévole peut identifier les transitions entre vendeurs). *Fix :* Ajouter une note dans l'Interaction Primitive "Impression" sur la structure du rouleau par vendeur.

- **[medium]** FR-055 — La génération automatique du bilan d'édition PDF en deux langues au moment de la clôture n'est pas spécifiée dans le composant "Contrôle de phase". Le retour visuel associé (spinner pendant la génération, toast de confirmation) est absent. *Fix :* Enrichir le composant "Contrôle de phase" avec un state pattern clôture : spinner + toast "Bilan d'édition généré (EN + FR)."

- **[medium]** FR-014 — Le comportement du bouton "Supprimer" sur la liste des éditions en phase post-Préparation n'est pas spécifié (absent ? désactivé avec tooltip ?). *Fix :* Dans le composant "Page Éditions — liste", spécifier que l'action "Supprimer" est absente (ou désactivée + tooltip) pour toute édition ayant dépassé la phase Préparation.

- **[medium]** FR-019 — Les champs obligatoires du profil vendeur et leurs validations (format email, format téléphone) ne sont pas spécifiés dans le composant "Formulaire dépôt". *Fix :* Ajouter la liste des champs (nom, prénom, email obligatoire, téléphone obligatoire) et leurs validations dans le composant Formulaire dépôt.

- **[medium]** FR-041 — La structure de la facture acheteur (liste articles + prix unitaire, total, nom association, nom édition, date ; lots sur une ligne unique) n'est spécifiée que dans le PRD. *Fix :* Ajouter dans le composant "Panier POS" — section état post-validation — une note sur la structure attendue du PDF facture avec les champs obligatoires.

- **[low]** FR-027 — Le layout visuel de l'étiquette standard (ordre exact des champs, mention INCOMPLET conditionnelle, commentaire conditionnel, numéro de table, barcode XXXX-XXXX, absence du nom vendeur) n'est spécifié que dans le PRD. *Fix :* Ajouter un composant "Aperçu étiquette thermique" ou une note de structure dans le composant Formulaire dépôt pour guider l'implémentation du rendu étiquette.

- **[low]** FR-024 — La suppression d'article (phase Dépôt uniquement) n'a pas de spec UX : ni CTA décrit dans le catalogue, ni dialog de confirmation, ni post-état. *Fix :* Dans le composant Catalogue (accès admin phase Dépôt), ajouter le bouton "Supprimer l'article" (danger zone), dialog de confirmation, et toast post-suppression.

- **[low]** FR-010 — L'unicité de l'édition active n'a pas de traitement UX sur le bouton "Créer une édition" quand une édition est déjà active. *Fix :* Dans le composant "Page Éditions — liste", préciser le comportement du bouton "Créer une édition" si une édition active existe (désactivé + tooltip ou warning inline).

- **[low]** FR-061 — Le sélecteur de rôle lors de la création d'un utilisateur n'est pas contraint pour empêcher la création d'un second compte admin. *Fix :* Dans le composant "Page Utilisateurs", préciser que le rôle "Admin" n'est pas proposé dans le sélecteur de rôle lors de la création d'un compte bénévole.

- **[low]** FR-050 — Le contenu du bilan de vente (articles vendus, invendus avec table, total brut, commission déduite, net à reverser, lots sur une ligne) est décrit narrativement dans Flow 5 mais sans spec de composant formelle. *Fix :* Ajouter dans le composant "Récapitulatif reversement imprimable" une note sur la structure du bilan de vente imprimé (liste des champs attendus).
