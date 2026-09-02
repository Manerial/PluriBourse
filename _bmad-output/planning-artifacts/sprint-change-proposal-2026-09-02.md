---
title: "Proposition de changement de sprint : Prix et marqueur « (lot) » dans les catalogues"
date: 2026-09-02
status: approved
author: Manerial (via Claude Code)
---

# Proposition de changement de sprint : Prix et marqueur « (lot) » dans les catalogues

## 1. Résumé du problème

En utilisant le catalogue articles (Epic 6, Stories 6.1 et 6.2 `done`), Manerial a constaté deux défauts d'affichage liés aux lots :

1. **Catalogue actif** (`/admin/catalog`, `/volunteer/catalog`) — un article membre d'un lot a `Item.price = null` (le prix est porté par `Lot.globalPrice`, convention `ItemPricing` de toute l'application). `ItemCatalogDto` transporte déjà `lotId` / `lotName` mais **pas** le prix du lot, et le template n'affiche ni l'un ni l'autre : la cellule prix est **vide** pour chaque membre de lot.
2. **Catalogue archivé** (`/admin/archived-catalog`) — chaque membre de lot est archivé comme sa propre ligne `archived_items`, portant déjà le prix global du lot dans `price` (`EditionArchivingService`), mais **sans aucune indication qu'il s'agit d'un lot**. Un lot de 3 articles à 10 € se lit comme trois articles indépendants à 10 €.

**Type de changement :** nouvelle exigence émergée d'un constat d'usage (pas de limitation technique, pas de pivot).

**Objectif première itération** (volontairement réduit) : afficher le **prix du lot + un marqueur « (lot) »** (ex. « 10 € (lot) ») dans les deux catalogues. **Sans** regroupement repliable/dépliable des membres — ce dernier a été discuté puis reporté d'un commun accord (enjeux de pagination/tri côté serveur trop lourds pour cette itération).

### Constat de dérive pré-existante

Le PRD **FR-102** affirme que « le prix […] ne [sont] plus disponibles après archivage, par construction ». Or `archived_items.price` **existe déjà** (migration Liquibase `030-archived-items-price.xml`, introduite dans le commit « Code review 2 » après la Story 6.2), sans proposition de changement ni mise à jour du PRD. Cette proposition en profite pour **documenter rétroactivement** cette dérive.

### Preuves

- `ItemCatalogDto` (`domain/item/dto/ItemCatalogDto.java`) : champs `lotId`, `lotName`, pas de `lotPrice`.
- `item-catalog.component.html:94` : cellule prix rendue vide quand `item.price` est `null`.
- `EditionArchivingService.java:74-76` : `archivedItem.setPrice(item.getLot() != null ? item.getLot().getGlobalPrice() : item.getPrice())`.
- `archived_items` : aucune colonne liée au lot (`ArchivedItem.java`).
- `ArchivedCatalogIT.java:97-155` : storyboard existant archivant « Lot Duo » (8,00 €, membres « Duo A » / « Duo B ») — les deux lignes portent 8,00 € sans marqueur.
- PRD `prd.md` FR-088 (ligne 159), FR-102 (ligne 348) ; migration `030-archived-items-price.xml`.

## 2. Analyse d'impact

### Impact sur les epics

- **Epic 6 (Catalogue articles)** — réalisable comme prévu. Une nouvelle **Story 6.3** s'ajoute après 6.2 (`done`). L'intro de l'Epic 6 couvre déjà la consultation du catalogue archivé — aucun changement d'intro.
- **Epic 2 (Cycle de vie des éditions)** — `done`. **FR-088 est amendé** (l'archivage conserve désormais le prix effectif + une référence de lot pour les membres de lot), mais **le code d'archivage évolue dans la Story 6.3**, pas dans une story 2.x rouverte. La Story 2.7 n'est pas reséquencée ni réécrite.
- Aucune epic invalidée, aucune nouvelle epic, aucun reséquencement.

### Conflits d'artefacts

| Artefact | Sections concernées | Nature du changement |
|---|---|---|
| PRD (`prd.md`) | FR-088 (ligne 159), FR-102 (ligne 348) | Amendement de 2 exigences existantes |
| Epics (`epics.md`) | Lignes de traçabilité FR-088 (300) et FR-102 (308) ; nouvelle Story 6.3 en fin de section Epic 6 | Mise en cohérence + ajout de story, pas de réécriture d'historique |
| Architecture (`architecture.md`) | Aucune | Aucun pattern / stack / contrat d'API touché. `archived_items` gagne 2 colonnes nullable — évolution de modèle au niveau story, aucune section d'architecture ne décrit cette table |
| UX (`DESIGN.md` / `EXPERIENCE.md`) | Aucune réécriture | Le marqueur « (lot) » est cohérent avec la convention lot déjà établie (FR-041/048 ; `EXPERIENCE.md:142/144` « le lot est une unité de vente, pas de prix individuel »). Détail visuel traité au moment de la story, posture identique à 6.2 |
| `sprint-status.yaml` | Section Epic 6 | Nouvelle entrée `6-3-...: backlog` + commentaire de contexte |

### Impact technique

**Partie A — catalogue actif** (additive, sans migration) :
- Back : `lotPrice` (`BigDecimal`) ajouté à `ItemCatalogDto` ; `@Mapping(target = "lotPrice", source = "lot.globalPrice")` sur `ItemMapper.toCatalogDto` (le one-liner existe déjà à l'identique sur `toDto`). La requête `findAllByEditionIdForCatalog` fait déjà `LEFT JOIN FETCH i.lot`. Tri par prix inchangé (reste sur `Item.price`).
- Front : `lotPrice` dans le modèle `ItemCatalogDto` ; cellule prix → clé i18n `catalog.columns.priceLotFormat` si `lotId != null`.

**Partie B — catalogue archivé** (avec migration) :
- Migration `034-archived-item-lot.xml` : `addColumn` sur `archived_items` — `lot_ref` `BIGINT` nullable (**sans FK** : discriminant historique ; l'archivage supprime les `items` mais pas les `lots`, un futur nettoyage des `lots` orphelins ne doit pas casser une archive) et `lot_name` `VARCHAR(200)` nullable. Aucun changeset de rétro-remplissage.
- Back : 2 champs sur l'entité `ArchivedItem` ; `EditionArchivingService` peuple `lot_ref` / `lot_name` à l'archivage ; 2 champs sur `ArchivedItemDto` (mapping MapStruct 1:1, pas d'annotation). `ArchivedItemService` inchangé (pas de tri ni de filtre lot).
- Front : 2 champs sur le modèle `ArchivedItemDto` ; cellule prix → clé i18n `admin.archivedCatalog.priceLotFormat` si `lotRef != null`.

- Tests : extension des storyboards `ItemCatalogIT` (ajout d'un lot — il n'en crée pas aujourd'hui) et `ArchivedCatalogIT` (lot « Lot Duo » déjà présent + ajout d'un 2ᵉ lot homonyme pour prouver la distinction `lot_ref`). Garde de non-régression `EditionArchivingIT` (colonnes nullable additives).
- Aucun impact infrastructure / déploiement / CI.
- **Choix `lot_ref` (id opaque) plutôt qu'un booléen `is_lot`** : `archived_items` ne stocke ni vendeur ni identité de lot ; deux lots homonymes (même d'un même vendeur) seraient indistinguables avec un simple booléen ou le seul nom. L'id d'origine du lot donne un discriminant stable et rend bon marché une future story de regroupement.

## 3. Approche recommandée

**Option retenue : Ajustement direct (Option 1)** — nouvelle Story 6.3 dans l'Epic 6 + amendement de FR-088 et FR-102.

**Justification :**
- Changement **purement additif** : aucune ligne de code déjà livrée n'est invalidée. Les Stories 6.1 et 6.2 restent correctes pour leur périmètre.
- Le marqueur « prix du lot, pas de prix individuel » est **déjà la convention** du PRD et de l'UX partout ailleurs (étiquette, facture, bilan) — on l'étend aux catalogues.
- Les amendements FR-088 / FR-102 ne font que **réaligner le PRD** sur une décision produit délibérée (et documenter au passage la dérive `price` déjà en production).
- **Rollback (Option 2)** : non applicable — rien à annuler.
- **Revue MVP (Option 3)** : non applicable — MVP non affecté, aucun périmètre réduit.

**Effort estimé :** faible à moyen (1 migration additive + mappings DTO + templates/i18n dans 2 écrans + extension de 2 storyboards de test). **Risque :** faible.

## 4. Propositions de changement détaillées

### 4.1 — PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`), FR-088

**ANCIEN :**
> Après clôture, l'admin peut déclencher une action **« Archiver l'Édition »** qui : (1) copie chaque article de l'édition dans une table d'archivage avec son nom, sa catégorie et son statut (vendu ou invendu) — les articles de lot sont archivés individuellement, sans conserver la notion de lot ; (2) supprime définitivement les enregistrements articles et les profils vendeurs de cette édition. Après archivage, le retour en arrière vers Post-vente est définitivement désactivé pour cette édition. Cette action nécessite une confirmation explicite.

**NOUVEAU :**
> Après clôture, l'admin peut déclencher une action **« Archiver l'Édition »** qui : (1) copie chaque article de l'édition dans une table d'archivage avec son nom, sa catégorie, son statut (vendu ou invendu), son **prix effectif** (prix de l'article, ou prix global du lot pour un article membre d'un lot) et, pour un article membre d'un lot, une **référence de lot** (identifiant du lot d'origine + nom du lot) permettant de regrouper les membres d'un même lot et de distinguer deux lots homonymes ; les autres données au niveau article (code-barres, table, vendeur) ne sont pas conservées ; (2) supprime définitivement les enregistrements articles et les profils vendeurs de cette édition. Après archivage, le retour en arrière vers Post-vente est définitivement désactivé pour cette édition. Cette action nécessite une confirmation explicite.

### 4.2 — PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`), FR-102

**ANCIEN :**
> L'administrateur peut consulter le catalogue archivé d'une édition passée (déjà Archivée), via un sélecteur d'édition. La liste est filtrable et triable sur les seules données conservées par l'archivage (FR-088) : nom, catégorie, statut vendu/invendu — le prix, le code-barres, la table et le vendeur ne sont plus disponibles après archivage, par construction. Cette consultation sert de brique de base pour de futures statistiques comparatives entre éditions (hors scope de cette exigence). Réservé aux administrateurs. Dépend de la Story 2.7 (mécanisme d'archivage). *(2026-07-29, voir `sprint-change-proposal-2026-07-29.md`)*

**NOUVEAU :**
> L'administrateur peut consulter le catalogue archivé d'une édition passée (déjà Archivée), via un sélecteur d'édition. La liste est filtrable et triable sur les données conservées par l'archivage (FR-088) : nom, catégorie, statut vendu/invendu, prix. Le prix d'un article membre d'un lot est le prix global du lot, affiché avec un marqueur « (lot) » ; le code-barres, la table et le vendeur ne sont plus disponibles après archivage, par construction. Cette consultation sert de brique de base pour de futures statistiques comparatives entre éditions (hors scope de cette exigence). Réservé aux administrateurs. Dépend de la Story 2.7 (mécanisme d'archivage). *(2026-07-29, voir `sprint-change-proposal-2026-07-29.md` ; prix + marqueur lot ajoutés 2026-09-02, voir `sprint-change-proposal-2026-09-02.md`)*

### 4.3 — Epics (`epics.md`), ligne de traçabilité FR-088 (ligne 300)

**ANCIEN :** `- FR-088 : Epic 2 — « Archivage de l'édition » archive chaque article (nom, catégorie, statut) puis supprime les enregistrements ; désactive le retour arrière vers Post-vente`

**NOUVEAU :** `- FR-088 : Epic 2 — « Archivage de l'édition » archive chaque article (nom, catégorie, statut, prix effectif, + référence de lot pour les membres de lot) puis supprime les enregistrements (code-barres, table, vendeur non conservés) ; désactive le retour arrière vers Post-vente`

### 4.4 — Epics (`epics.md`), ligne de traçabilité FR-102 (ligne 308)

**ANCIEN :** `- FR-102 : Epic 6 — Consultation du catalogue archivé d'une édition passée (admin uniquement, dépend de la Story 2.7) — addendum 2026-07-29`

**NOUVEAU :** `- FR-102 : Epic 6 — Consultation du catalogue archivé d'une édition passée (admin uniquement, dépend de la Story 2.7) : nom, catégorie, statut, prix (marqueur « (lot) » pour les membres de lot) — addendum 2026-07-29, prix + marqueur lot 2026-09-02`

### 4.5 — Epics (`epics.md`), nouvelle Story 6.3 (ajoutée après la Story 6.2)

```markdown
### Story 6.3 : Prix et marqueur « (lot) » dans les catalogues

Ajoutée le 2026-09-02, voir `sprint-change-proposal-2026-09-02.md`. Amende FR-088 et FR-102.

En tant qu'administrateur ou bénévole,
je veux que les articles membres d'un lot affichent le prix du lot avec un marqueur « (lot) » dans le catalogue actif comme dans le catalogue archivé,
afin de disposer d'un prix lisible pour les membres de lot (vide aujourd'hui dans le catalogue actif) et de distinguer d'un coup d'œil une ligne de lot d'un article individuel.

**Critères d'acceptation :**

**Étant donné** un article appartenant à un lot dans le catalogue actif
**Quand** la liste s'affiche
**Alors** sa cellule prix affiche le prix global du lot suivi d'un marqueur « (lot) » (ex. « 10 € (lot) »)
**Et** un article individuel affiche son propre prix, inchangé

**Étant donné** l'API `GET /api/catalog`
**Quand** la page est renvoyée
**Alors** chaque entrée ayant un `lotId` porte aussi `lotPrice` égal au prix global du lot
**Et** les entrées individuelles portent `lotPrice = null` ; `price` reste `null` pour les membres de lot (inchangé)

**Étant donné** un article archivé ayant appartenu à un lot
**Quand** le catalogue archivé s'affiche
**Alors** sa cellule prix affiche le prix (= prix global du lot, déjà archivé) suivi d'un marqueur « (lot) »
**Et** un article archivé individuel est inchangé

**Étant donné** qu'une édition est archivée
**Quand** les lignes d'archive sont écrites
**Alors** chaque ligne membre d'un lot stocke l'identifiant du lot d'origine (`lot_ref`) et le nom du lot (`lot_name`)
**Et** les lignes d'articles individuels stockent `null` pour ces deux champs

**Étant donné** deux lots différents de la même édition portant le même nom
**Quand** l'édition est archivée
**Alors** leurs membres archivés portent des valeurs `lot_ref` différentes (les deux lots restent distinguables) malgré un `lot_name` identique

**Étant donné** l'API `GET /api/admin/archive/editions/{id}/items`
**Quand** la page est renvoyée
**Alors** chaque entrée porte `lotRef` et `lotName` (`null` pour les articles individuels)

**Étant donné** qu'un bénévole (non admin) appelle l'endpoint du catalogue archivé
**Quand** la requête est envoyée
**Alors** l'accès est toujours refusé (403) — inchangé

**Étant donné** une édition archivée avant cette migration
**Quand** son catalogue archivé est consulté
**Alors** ses lignes existantes conservent `lot_ref = null` / `lot_name = null` et leurs membres s'affichent sans marqueur — accepté tel quel (données de dev, seront réinitialisées)

**Hors périmètre :** regroupement repliable/dépliable des membres de lot en une seule ligne (dans les deux catalogues) ; tri du catalogue actif sur le prix effectif du lot (reste sur `Item.price`) ; filtre `nom` sur le nom de lot ; rétro-remplissage de `lot_ref`/`lot_name` sur les éditions déjà archivées.
```

### 4.6 — `sprint-status.yaml` (section Epic 6, après `6-2-consultation-catalogue-edition-archivee: done`)

```yaml
  # Sprint change proposal 2026-09-02 : les articles membres d'un lot n'ont pas de prix
  # lisible dans le catalogue actif (Item.price null, prix porté par Lot.globalPrice) et
  # aucun marqueur de lot dans le catalogue archivé. Story 6.3 : afficher le prix du lot +
  # marqueur « (lot) » dans les deux catalogues (pas de regroupement repliable — reporté).
  # Amende FR-088 + FR-102 (l'archive conserve désormais prix effectif + lot_ref/lot_name).
  # Migration 034 : colonnes lot_ref (BIGINT nullable, discriminant opaque sans FK) + lot_name
  # sur archived_items, peuplées par EditionArchivingService. Éditions déjà archivées non
  # rétro-remplies (base de dev, sera réinitialisée).
  6-3-prix-du-lot-dans-les-catalogues: backlog
```

## 5. Implémentation — Handoff

**Scope de changement : Mineur.** Amendement de 2 exigences existantes + ajout de 1 story + 1 entrée `sprint-status.yaml`, sans remise en cause de code déjà livré.

**Responsabilités :**
- **Artefacts de planification** (PRD `prd.md`, `epics.md`, `sprint-status.yaml`) : appliqués immédiatement à l'approbation de cette proposition (éditions 4.1 à 4.6).
- **Story 6.3** : à régénérer proprement via `bmad-create-story` (un brouillon manuel existe déjà dans `_bmad-output/implementation-artifacts/6-3-prix-du-lot-dans-les-catalogues.md`, à utiliser comme matière première puis remplacer). `create-story` passera l'entrée `sprint-status.yaml` de `backlog` à `ready-for-dev`.
- **Implémentation** : Dev agent (`dev-story`) — Parties A + B dans le **même commit**.

**Critères de succès :** FR-088 et FR-102 amendés dans `prd.md` et `epics.md` ; Story 6.3 présente dans `epics.md` ; `sprint-status.yaml` reflète la nouvelle entrée ; Story 6.3 régénérée via le workflow BMAD ; implémentation livrée avec extension des storyboards `ItemCatalogIT` + `ArchivedCatalogIT` et non-régression de la suite complète (back + front).
