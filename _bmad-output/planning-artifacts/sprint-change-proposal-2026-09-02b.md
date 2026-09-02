---
title: "Proposition de changement de sprint : Post-vente — accès Dépôt, documents vendeur & lots partiels"
date: 2026-09-02
status: approved
approved_by: Manerial
approved_date: 2026-09-02
author: Manerial (via Claude Code)
supersedes_decision: "Décision de suivi 2026-08-24 sur la réimpression du bordereau (jamais formalisée en SCP, uniquement PhaseGuard.requireDepositPhaseForSlipReprint)"
---

# Proposition de changement de sprint : Post-vente — accès Dépôt, documents vendeur & lots partiels

> Numérotée `-2026-09-02b` pour ne pas écraser `sprint-change-proposal-2026-09-02.md` (approuvée, « Prix et marqueur (lot) dans les catalogues », Story 6.3).

**Déclencheur :** en testant la phase Post-vente comme bénévole (2026-09-02), Manerial a relevé six points — cinq évolutions et un bug de fond — sur des épics tous à `done`. Il ne s'agit pas de compléter un backlog mais de faire évoluer un système déjà testé, comme la SCP 2026-08-24.

**Mode :** revue incrémentale, point par point, avec vérification du code réel avant chaque proposition.

---

## 1. Résumé des problèmes identifiés

| # | Sujet | Catégorie BMAD |
|---|---|---|
| T1 | L'onglet **Dépôt reste accessible en Post-vente** et affiche un workflow de dépôt complet (recherche/création de profil, formulaires article/lot, édition/suppression) alors que seule la réimpression a du sens → clics qui échouent en **422** (`item-modification-locked`). | Nouvelle exigence + nettoyage UX. La seule raison d'être de cet accès (réimpression du bordereau) a déjà été retirée le 2026-08-24 ; la réimpression d'étiquettes qui subsistait est vestigiale. |
| T2 | `volunteer.deposit.error.search` / `.noActiveEdition` **affichés en clair** au lieu d'être traduits. | **Bug** — clé `volunteer.deposit.error` **dupliquée** dans `fr.json` / `en.json` (le second bloc, `reprintSlip`/`a4PrinterUnavailable`/…, écrase le premier). **Correctif déjà appliqué dans la session** : les deux blocs fusionnés en un seul (6 clés), scan de doublons de clés sur toute la surface des deux fichiers → aucun autre doublon. Consigné ici, pas de story. |
| T3 | **Un lot peut être vendu plusieurs fois au prix global entier.** Au POS, scanner le code-barres d'un membre de lot ajoute ce seul membre ; `scan()` ne rejette que si **cet article précis** est `sold`. Rien n'empêche de vendre séparément les membres restants d'un lot déjà (partiellement) vendu — chacun facturé au **prix global du lot** (`ItemPricing.computeTotal` ajoute `lot.globalPrice` dès qu'un membre est présent). Le lot est encaissé N fois, l'écart n'est affecté à personne (gonfle les rapports de ventes, pas le reversement vendeur). | **Bug de fond.** FR-047 autorise explicitement la validation d'un lot incomplet mais n'a **jamais défini** le sort des membres non scannés. |
| T4 | Le **bilan de vente** ne distingue pas, au sein d'un lot, les articles vendus des invendus (`distinctByLot` → 1 ligne/lot, « lot avec ≥1 membre vendu = vendu en entier »). Le vendeur ne sait pas quels articles physiques récupérer. | Nouvelle exigence — amende FR-050. |
| T5 | Le bouton « Imprimer le bilan » est **toujours visible**, même avant solde ; aucune impression automatique au moment de solder. | Nouvelle exigence UX — amende FR-095 / UX-DR22. |
| T6 | Le **bordereau de dépôt** affiche un lot en une seule ligne (nom + prix global), **sans aucun détail des membres** — et ces membres ne sont listés nulle part sur le PDF. | Nouvelle exigence — amende FR-031. |

### Preuves (code réel)

- `PosScanService.scan()` : `if (item.isSold()) throw new ItemAlreadySoldException(...)` — garde uniquement sur l'article scanné, aucune notion de lot.
- `PosBasketService.addItem()` : ajoute `scanned.itemId()` (un seul article), pas le lot.
- `ItemPricing.computeTotal()` : `for (Item item : distinctByLot(items)) total += (item.getLot() != null ? item.getLot().getGlobalPrice() : item.getPrice())` → prix global compté une fois par lot présent, quel que soit le nombre de membres.
- `DepositSlipRenderer.buildItemsTable()` / `SettlementReportRenderer.buildSoldItemsTable()` : `ItemPricing.distinctByLot()` → 1 ligne par lot, aucun détail des membres.
- `SettlementService.getAmountPaid()` : `.filter(s -> s.getStatus() == SettlementStatus.SETTLED).map(Settlement::getAmount).orElse(null)` ; `SettlementReportRenderer` : `if (amountPaid != null)` → ligne « Montant remis » omise sinon.
- `PhaseGuard.requireDepositOrPostSalePhase()` (autorise Dépôt + Post-vente pour la fiche vendeur) et `requireDepositPhaseForSlipReprint()` (Dépôt seul pour le bordereau — commentaire « follow-up decision, 2026-08-24 »).
- `app-layout.component.html` : entrée sidebar `/volunteer/deposit` conditionnée `phase === 'DEPOSIT' || phase === 'POST_SALE'` ; `depositPhaseGuard` autorise `DEPOSIT || POST_SALE`.
- Story 3.6 (epics.md) AC : « le bénévole consulte la fiche vendeur (**en phase Dépôt ou Post-vente**) … l'impression est toujours rejouable ».
- `volunteer.deposit.error` : deux blocs distincts dans `fr.json` (lignes ~327 et ~436) et `en.json`.

### Constat de dérive documentaire pré-existante

Les **FR-105 à FR-108** définis par la SCP 2026-08-24 (devise par édition, Préparation non exclusive, case impression facture, Liste des ventes) **n'ont jamais été reportés** dans `prd.md` ni `epics.md`. De même, la décision « réimpression du bordereau restreinte à la phase Dépôt » n'existe que dans un commentaire de `PhaseGuard`. La présente SCP :

- part de **FR-109** pour la nouvelle exigence (T3) ;
- **formalise** la restriction Dépôt du bordereau (P1 / P7) ;
- ne backporte pas FR-105–108 (hors périmètre) mais le signale pour traitement séparé.

---

## 2. Analyse d'impact

### Impact sur les epics

Les 6 epics sont `done`. Trois sont amendés de façon incrémentale (même patron que la SCP 2026-08-24) ; **aucun nouvel epic, aucun epic obsolète, aucun reséquencement**.

- **Epic 3 — Enregistrement des vendeurs & Dépôt** : Story 3.6 amendée (bordereau + tableau détail des lots ; réimpression restreinte à la phase Dépôt ; fiche vendeur `/volunteer/deposit` non accessible en Post-vente). Intro d'epic inchangée.
- **Epic 4 — Point de vente** : Story 4.3 amendée (nouvelle règle FR-109 : un lot ne se vend qu'une fois). L'intro d'epic mentionne déjà « respect de l'intégrité des lots » — inchangée.
- **Epic 5 — Post-vente, Reversements & Rapports** : Story 5.1 amendée (case « Imprimer le bilan » au solde, cochée par défaut ; bouton bilan réservé aux vendeurs soldés / non réclamés) ; Story 5.2 amendée (bilan restructuré : table unifiée + statut + table éclatée des lots + ligne de comptage). Intro d'epic inchangée.

### Impact sur les stories

| Story | Statut actuel | Changement |
|---|---|---|
| 3.6 — Bordereau de dépôt PDF | done | AC amendés (P7). Livraison du changement dans la **nouvelle story** (partie D), pas dans une 3.6 rouverte. |
| 4.3 — Gestion des lots au POS | done | Nouveau bloc AC (P8). Livraison dans la nouvelle story (partie C). |
| 5.1 — Flux de solde | done | AC amendés (P9). Livraison dans la nouvelle story (partie B). |
| 5.2 — Bilan de vente PDF | done | AC amendés (P10). Livraison dans la nouvelle story (partie E). |
| — | — | **Partie A** (retrait accès Dépôt Post-vente) ne rouvre aucune story : c'est un retrait de navigation + resserrement de gardes. |

### Conflits d'artefacts

- **PRD** — FR-031, FR-047, FR-050, FR-095 amendés ; **FR-109 créé** (P1–P5). FR-065 : aucun changement de texte (« reversement en phase Post-vente » déjà correct — le code dérivait). **MVP non affecté**, aucune réduction de périmètre.
- **Epics** — lignes miroir des FR + section couverture + UX-DR22 (P11–P12), en cohérence avec le PRD.
- **Architecture** — section « Concurrence — POS » : ajout d'une ligne « Intégrité des lots » + complément à l'exigence de test (P13). Pipeline d'impression inchangé (seul le contenu des renderers évolue). Pas de changement de stack, de modèle de données majeur ni de contrat d'API (hors nouveau type d'exception + slug d'erreur 409).
- **UX** — UX-DR22 amendé (P12). Nav bénévole phase-adaptative : entrée Dépôt retirée en Post-vente (aligne le code sur FR-065). Pas de refonte de wireframe.
- **Tests** — `DepositSlip*` / `SettlementReport*PrintingIT` (grep d'octets PDF → cassent, nouvelles assertions requises) ; `SaleConcurrencyIT` (+ scénario lot verrouillé) ; `app-layout.component.spec`, `settlement-list.component.spec`, `seller-search.component.spec`.
- **i18n** — `messages_{fr,en}.properties` (nouvelles clés PDF : colonnes des tableaux détail/unifié, statut, ligne de comptage) ; `{fr,en}.json` (clé d'erreur lot verrouillé, case impression au solde) ; **correctif clé dupliquée `volunteer.deposit.error` déjà appliqué**.
- **Docs** — change logs des stories 3.6 / 5.1 / 5.2 ; la décision 2026-08-24 sur la réimpression du bordereau est **formalisée et supersédée** par la présente SCP.
- **Aucun impact** : CI/CD, IaC, déploiement, monitoring.

### Impact technique (synthèse)

| Zone | Fichiers principaux |
|---|---|
| Partie A — retrait accès Dépôt Post-vente | `app-layout.component.html` (`@if phase === 'DEPOSIT'`), `core/guards/deposit-phase.guard.ts`, `PhaseGuard.requireDepositOrPostSalePhase` (→ Dépôt seul), `PhaseGuard.requireDepositPhaseForSlipReprint` (devient redondant) |
| Partie B — flux de solde | `settlement-list.component.{ts,html}` (case dans le formulaire de solde ; visibilité du bouton par statut ; impression best-effort post-solde, patron auto-print facture Story 4.7), `{fr,en}.json` |
| Partie C — verrouillage lot (FR-109) | `PosScanService` (garde au scan : rejet si un frère `sold`), `PosBasketService.validate` (garde à la validation, comme le pré-check `alreadySold`), nouvelle exception + slug `lot-already-sold`, front `handleScanError`, `{fr,en}.json` |
| Partie D — bordereau détail lots | `DepositSlipRenderer` (nouveau tableau), `messages_{fr,en}.properties` |
| Partie E — bilan restructuré | `SettlementReportRenderer` (table unifiée + colonne statut + table éclatée des membres + ligne de comptage ; comptage = `sold` réel par membre, `vendus + invendus = déposés` ; prix global d'un lot partiel compté une fois), `messages_{fr,en}.properties` |

---

## 3. Approche recommandée

**Ajustement direct** (Option 1 du checklist correct-course) via **une story unique multi-parties** amendant les Epics 3/4/5 — même patron que la SCP 2026-08-24.

- **Pas de rollback** : les six points sont additifs ou correctifs ; T1 retire un accès mais c'est un changement vers l'avant, pas la correction d'un travail cassé.
- **Pas de revue MVP** : le MVP livré (6 epics) est intact, aucune réduction de périmètre.
- Une seule story (et non 3–4) : les parties se recoupent fortement (`SettlementReportRenderer` partage ses helpers avec `DepositSlipRenderer` ; `messages_{fr,en}.properties` ; les IT qui grep les octets PDF ; la page Reversements). Un document unique évite qu'une des ~15 décisions de cadrage ne passe à la trappe.

**Effort : Moyen. Risque : Faible-Moyen** — points sensibles : la restructuration du renderer bilan (parties C/E interagissent : le comptage et le statut par membre reposent sur le `sold` réel, pas sur la normalisation « lot vendu en entier » de la Story 5.2), et la garde de concurrence lot (partie C). Les IT qui grep les octets du PDF sont fragiles et devront être réécrits.

---

## 4. Propositions de changement détaillées

> Toutes approuvées en revue incrémentale (2026-09-02).

### Groupe A — PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`)

**P1 — FR-031 (amender)**
- OLD : *Un bordereau de dépôt est imprimable par vendeur : liste des articles, prix unitaires et reversement net attendu après commission.*
- NEW : *Un bordereau de dépôt est imprimable par vendeur **en phase Dépôt uniquement** : liste des articles avec prix (un lot sur une ligne unique — nom + prix global), taux de commission, reversement net attendu, **et un tableau « détail des lots » listant chaque article membre d'un lot (nom du lot, catégorie du lot, nom de l'article)**.*

**P2 — FR-047 (amender)**
- OLD : *Si le lot n'est pas complet lors de la validation, une notification inline avertissement est affichée dans le panier, mais la validation du paiement n'est pas bloquée — le caissier peut valider un lot incomplet.*
- NEW : *(inchangé)* … le caissier peut valider un lot incomplet. **Dès lors qu'au moins un article du lot est vendu, le lot est réputé vendu comme un tout : les articles restants deviennent non-vendables et reviennent au vendeur (voir FR-109).**

**P3 — FR-109 (nouveau)**
- NEW : *Un lot ne peut être vendu qu'une seule fois. Dès qu'un de ses articles est marqué vendu, scanner un autre article du même lot au POS est rejeté avec une erreur 409 explicite (« article appartenant à un lot déjà vendu »), **au scan et à la validation du panier** (course multi-postes). Le prix global du lot est encaissé une seule fois (FR-048). Les articles non vendus d'un lot vendu reviennent au vendeur et figurent comme invendus au bilan de vente (FR-050).*

**P4 — FR-050 (amender)**
- OLD : *Le bilan de vente contient : articles vendus (nom, prix unitaire), invendus (nom, catégorie, numéro de table), total brut, commission déduite, montant net à reverser. Un lot apparaît sur une ligne unique (nom du lot, prix du lot).*
- NEW : *Le bilan de vente contient : (1) **un tableau unifié des articles** (nom, catégorie, table, prix, **statut vendu/invendu**), un lot y apparaissant sur une ligne unique (nom du lot, prix du lot, statut « vendu » si au moins un article du lot est vendu, prix global compté une seule fois) ; (2) **un tableau « détail des lots »** listant chaque article membre d'un lot avec son statut réel, pour indiquer au vendeur les articles à récupérer ; (3) **une ligne de comptage** : nombre d'articles vendus, invendus, déposés (1 article = 1 unité, `vendus + invendus = déposés`) ; (4) total brut, commission déduite, montant net à reverser ; (5) **le montant remis, uniquement si le vendeur a été soldé**.*

**P5 — FR-095 (amender)**
- OLD : *… Chaque ligne comporte les actions : imprimer le bilan de vente, accéder au formulaire de solde, marquer comme non réclamé. …*
- NEW : *… Chaque ligne comporte les actions : accéder au formulaire de solde et marquer comme non réclamé (**vendeurs non soldés**) ; imprimer le bilan de vente (**vendeurs soldés ou non réclamés uniquement**, pour ré-impression après échec). **Le formulaire de solde comporte une case « Imprimer le bilan de vente », cochée par défaut, qui met le bilan en file d'impression à la confirmation du solde (best-effort — un échec d'impression n'annule pas le solde).** …*

**P6 — FR-065** — pas d'édition. « Reversement en phase Post-vente » est déjà correct ; le retrait de l'onglet Dépôt en Post-vente (partie A) aligne le code sur ce FR.

### Groupe B — Epics : critères d'acceptation (`epics.md`)

**P7 — Story 3.6**
- Bloc « il contient » : ajouter *« **Et** un tableau « détail des lots » liste chaque article membre d'un lot : nom du lot, catégorie du lot, nom de l'article »*.
- Bloc réimpression :
  - OLD : *Étant donné que le bénévole consulte la fiche vendeur (en phase Dépôt ou Post-vente) / Quand il clique sur « Réimprimer le bordereau » / Alors le bordereau est régénéré et remis en file d'attente — l'impression est toujours rejouable depuis la fiche vendeur*
  - NEW : *Étant donné que le bénévole consulte la fiche vendeur **en phase Dépôt** / Quand il clique sur « Réimprimer le bordereau » / Alors le bordereau est régénéré et remis en file d'attente / **Et** en phase Post-vente, la fiche vendeur (`/volunteer/deposit`) n'est plus accessible (ni entrée de navigation, ni route active) — le bilan de vente (Story 5.2) est le document de référence du vendeur en Post-vente*

**P8 — Story 4.3** (nouveau bloc, après « … vendu à son prix global (FR-048) »)
- NEW : *Étant donné qu'au moins un article d'un lot a été vendu / Quand un caissier scanne un autre article du même lot / Alors le scan est rejeté avec une erreur explicite (« cet article appartient à un lot déjà vendu ») — au scan **et** à la validation du panier (course multi-postes) / **Et** les articles non vendus du lot reviennent au vendeur et apparaissent comme invendus au bilan (FR-109)*

**P9 — Story 5.1**
- OLD : *Étant donné qu'un vendeur a été soldé / Quand le bénévole consulte la liste de solde / Alors un bouton « Imprimer le bilan de vente » est disponible pour ce vendeur (UX-DR22) / Et cliquer dessus met le PDF en file d'attente pour impression A4 avec retour visuel spinner et toast*
- NEW :
  - *Étant donné que le bénévole ouvre le formulaire de solde d'un vendeur / Alors une case « Imprimer le bilan de vente » y est présente, cochée par défaut / Et à la confirmation du solde, si la case est cochée, le bilan est mis en file d'impression A4 (best-effort — un échec d'impression n'annule pas le solde)*
  - *Étant donné qu'un vendeur est soldé ou marqué non réclamé / Quand le bénévole consulte la liste de solde / Alors un bouton « Imprimer le bilan de vente » est disponible pour ce vendeur (ré-impression, UX-DR22), avec retour visuel spinner et toast / **Et** ce bouton est masqué pour les vendeurs non soldés*

**P10 — Story 5.2**
- OLD : *Alors il contient : articles vendus (nom, prix unitaire), articles invendus (nom, catégorie, numéro de table), total brut, commission déduite, montant net à reverser (FR-050) / Et un lot apparaît sur une seule ligne (nom du lot, prix du lot)*
- NEW : *Alors il contient : (1) un tableau unifié des articles — nom, catégorie, table, prix, **statut (vendu/invendu)** — un lot sur une ligne unique (statut « vendu » si ≥1 article du lot vendu, prix global compté une fois) ; (2) **un tableau « détail des lots »** — nom du lot, article, catégorie, table, statut réel par article — pour indiquer les articles à récupérer ; (3) une **ligne de comptage** : articles vendus / invendus / déposés (1 article = 1 unité, `vendus + invendus = déposés`) ; (4) total brut, commission déduite, montant net à reverser (FR-050) ; (5) le montant remis, **uniquement si le vendeur est soldé***

### Groupe C — Epics : lignes miroir des FR + UX-DR22 (`epics.md`)

**P11 — Lignes miroir des FR**
- Ligne 65 (FR-031) → *« … imprimable par vendeur **en phase Dépôt** : articles + prix (lot = 1 ligne), taux de commission, reversement net attendu, **+ tableau « détail des lots » (nom du lot, catégorie du lot, article)** »*.
- Ligne 89 (FR-047) → ajouter *« Dès qu'un article du lot est vendu, le lot est réputé vendu comme un tout ; les articles restants reviennent au vendeur (FR-109). »*
- Ligne 97 (FR-050) → *« … tableau unifié des articles avec **statut vendu/invendu** (lot = 1 ligne), **tableau « détail des lots »** (statut réel par article), **ligne de comptage vendus/invendus/déposés**, total brut, commission, reversement net, **montant remis si soldé** »*.
- Lignes 95 et 267 (FR-095) → *« … actions par ligne : solder et marquer non réclamé (non soldés), imprimer le bilan (**soldés / non réclamés uniquement**) ; **case « Imprimer le bilan » cochée par défaut dans le formulaire de solde** »*.
- Section FR de l'Epic 4 → ajouter : *« FR-109 : Epic 4 — Un lot ne se vend qu'une fois ; scan d'un article d'un lot déjà vendu rejeté (409, au scan et à la validation) ; articles restants rendus au vendeur. »*

**P12 — UX-DR22 (ligne 205)**
- OLD : *Implémenter le bouton d'impression bilan/reversement sur la liste de solde bénévole (par ligne vendeur, après solde) et sur la page de détail vendeur admin. Retour visuel spinner pendant la file d'attente, toast sur le résultat.*
- NEW : *Implémenter l'impression du bilan de vente : (1) case « Imprimer le bilan » dans le formulaire de solde, **cochée par défaut**, déclenchant l'impression à la confirmation du solde (best-effort) ; (2) bouton « Imprimer le bilan » par ligne, **visible uniquement pour les vendeurs soldés ou non réclamés** (ré-impression). Retour visuel spinner + toast dans les deux cas.*

### Groupe D — Architecture (`architecture.md`)

**P13 — Section « Concurrence — POS »**
- Nouvelle ligne au tableau, après « Scénario de conflit » :
  - *Intégrité des lots | Un lot ne se vend qu'une fois : dès qu'un article du lot est vendu, scanner un autre article du même lot est rejeté (**au scan et à la validation**, 409, même patron que le verrouillage optimiste `@Version`). Les articles non vendus d'un lot vendu reviennent au vendeur (FR-109) | Empêche le double-encaissement du prix global d'un lot ; l'« intégrité des lots » de F4 (déjà mentionnée) inclut désormais cette règle*
- Ligne « Exigence de test » : compléter par *« + un scénario couvrant le rejet du scan d'un article de lot déjà vendu, au scan et en course multi-postes à la validation »*.

**P14 — Frontière d'Impression** — pas d'édition. Pipeline inchangé ; seul le contenu de `DepositSlipRenderer` et `SettlementReportRenderer` évolue. Consigné ici.

---

## 5. Plan de transmission (handoff)

**Ampleur : Modérée.** Plusieurs stories `done` amendées + une nouvelle story ; changement de comportement backend (verrouillage lot) + restructuration de deux renderers PDF + retouches frontend. Pas de replan, MVP intact → **pas d'escalade PM/Architecte**.

| Élément | Ampleur | Transmis à |
|---|---|---|
| T2 (clé i18n dupliquée) | Mineure | **Déjà corrigé** dans la session — à vérifier visuellement (recherche vendeur en échec → message traduit) |
| Édites d'artefacts P1–P13 | Mineure | **Déjà appliqués (2026-09-02)** : `prd.md` (FR-031/047/050/095 + FR-109), `epics.md` (lignes miroir, carte de couverture, UX-DR22, ACs Stories 3-6/4-3/5-1/5-2), `architecture.md` (§ Concurrence — POS). |
| Parties A → E | Modérée | **PO / Dev — une nouvelle story via `bmad-create-story`**, multi-parties, analyse de code réel obligatoire (les IT qui grep les octets PDF, la garde de concurrence lot) |
| Backport FR-105–108 dans PRD/epics | Mineure, hors périmètre | À traiter séparément (dérive documentaire signalée, pas bloquante) |

### Parties de la story à créer

| Partie | Contenu | Déclencheur |
|---|---|---|
| **A** | Retrait de l'accès Dépôt en Post-vente : entrée sidebar (`@if phase === 'DEPOSIT'`), `depositPhaseGuard` et `PhaseGuard.requireDepositOrPostSalePhase` resserrés à Dépôt. Conséquence assumée : plus de réimpression d'étiquettes en Post-vente (un bouton dédié dans les actions du bilan pourra être ajouté plus tard si besoin). | T1 |
| **B** | Formulaire de solde : case « Imprimer le bilan de vente » cochée par défaut → impression best-effort à la confirmation. Bouton « Imprimer le bilan » par ligne masqué tant que `status == UNSETTLED` (visible pour soldés / non réclamés, ré-impression). | T5 |
| **C** | POS : verrouillage des lots partiellement vendus (FR-109). Garde au scan (`PosScanService` : rejet si un frère du lot est `sold`) **et** à la validation (`PosBasketService.validate`, comme le pré-check `alreadySold`). Nouveau type d'exception + slug `lot-already-sold` (409). Front : `handleScanError`. Test de concurrence réelle (Testcontainers MariaDB) : scénario dédié. | T3 |
| **D** | `DepositSlipRenderer` : nouveau tableau « détail des lots » (Lot · Catégorie du lot · Article), une ligne par membre, affiché seulement s'il existe au moins un lot. Pas de colonne prix (les membres n'ont pas de prix individuel). `messages_{fr,en}.properties`. | T6 |
| **E** | `SettlementReportRenderer` : (1) fusion des tableaux vendus/invendus en un tableau unifié avec colonne **Statut** (lot = 1 ligne, statut « vendu » si ≥1 membre vendu, prix global compté une fois) ; (2) tableau éclaté des membres de lots (Lot · Article · Catégorie · Table · Statut réel) ; (3) ligne de comptage (vendus / invendus / déposés, `sold` réel par membre, `vendus + invendus = déposés`). Le comptage et le statut par membre reposent sur `Item.isSold`, **pas** sur la normalisation « lot vendu en entier » de la Story 5.2 — attention à l'interaction avec le calcul monétaire (prix global inchangé). `messages_{fr,en}.properties`. | T4 |

### Points de conception figés (revue 2026-09-02)

- **Prix d'un lot partiellement vendu** : prix global entier, encaissé une seule fois (pas de prorata).
- **Comptage du bilan** : 1 article physique = 1 unité (`Item` rows), `sold` réel par membre.
- **Statut d'une ligne de lot** dans la table unifiée : 2 états (« vendu » si ≥1 membre vendu, sinon « invendu »).
- **Bouton « Imprimer le bilan »** : visible pour `status != UNSETTLED` (donc aussi « non réclamé »).
- **Catégorie du lot** = catégorie propre du `Lot` (Story 3.14), pas celle de chaque article membre.
- **Réimpression d'étiquettes en Post-vente** : supprimée, pas de remplacement dans cette itération.

### Critères de succès

- FR-109 : impossible de vendre deux fois le même lot ; l'acheteur du 2ᵉ membre reçoit un 409 explicite.
- Bilan : pour un lot partiellement vendu, le vendeur voit exactement quels articles récupérer (tableau éclaté) et un comptage cohérent (`vendus + invendus = déposés`).
- Bordereau : chaque membre de lot est listé (tableau détail).
- Post-vente : aucun accès à la fiche Dépôt, aucun 422 déclenchable depuis la navigation bénévole.
- Solde : bilan imprimé automatiquement à la confirmation (case cochée) ; bouton de ré-impression uniquement pour les vendeurs traités.
- Tous les tests backend + frontend verts ; `mvnw clean package` + `npm run build` OK.
