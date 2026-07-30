---
title: "Proposition de changement de sprint : Consultation du catalogue d'une édition archivée"
date: 2026-07-29
status: approved
author: Manerial (via Claude Code)
---

# Proposition de changement de sprint : Consultation du catalogue d'une édition archivée

## 1. Résumé du problème

Pendant la revue de code de la Story 6.1 (Catalogue articles — Liste filtrable & triable), un finding "decision needed" a révélé une incohérence entre `GET /categories` (scopé à l'édition active, `getActiveEdition()`) et `GET /catalog` (scopé à `getMostRecentEdition()`, incluant les éditions clôturées et archivées). En creusant l'intention réelle avec l'utilisateur, il est apparu que le besoin sous-jacent n'était pas de corriger cette divergence technique, mais de pouvoir **consulter le catalogue d'éditions passées** (ex. « je veux consulter 2024 »).

Vérification dans le PRD existant :
- **FR-086** scope explicitement le catalogue affiché par la Story 6.1 à l'édition active uniquement, et le rend indisponible après l'action Archiver l'Édition.
- **FR-088** (Story 2.7, toujours `backlog`) précise que l'archivage **copie chaque article dans une table d'archivage avec uniquement son nom, sa catégorie et son statut vendu/invendu**, puis **supprime définitivement** les enregistrements complets (prix, code-barres, table, vendeur) de la table principale.

Aucune des deux règles ne supporte une consultation multi-éditions telle quelle, et FR-088 rend même *techniquement impossible* une consultation complète (prix/code-barres/table/vendeur) d'une édition déjà archivée — cette donnée n'existe simplement plus.

**Clarification supplémentaire de l'utilisateur** : la table d'archivage (FR-088) est destinée à servir de base à de futures statistiques comparatives d'une édition à l'autre (ex. totaux vendus d'une année sur l'autre) — un besoin de reporting historique, pas seulement de consultation ponctuelle.

**Décision prise pendant la revue** : la Story 6.1 reste strictement scopée à l'édition active (`getActiveEdition()`, comme partout ailleurs dans l'application) — voir Change Log de `6-1-catalogue-articles-liste-filtrable-triable.md`, 2026-07-29. Le besoin de consultation d'éditions archivées est reporté à une nouvelle story dédiée, objet de cette proposition.

## 2. Analyse d'impact

### Impact sur les epics

- **Epic 6 (Catalogue articles)** — reste réalisable comme prévu. Une nouvelle story **6.2** s'ajoute après 6.1 (déjà `done`). Elle réutilise le même pattern d'interaction (liste filtrable/triable, `MatPaginator`) que 6.1, mais sur un jeu de données et un périmètre de filtres réduits (nom/catégorie/statut uniquement — le détail complet n'existe plus après archivage).
- **Epic 2 (Cycle de vie des éditions)** — la Story **2.7** (Clôture & Archivage de l'édition), toujours `backlog`, devient une **dépendance bloquante explicite** de la Story 6.2 : la table d'archivage que 2.7 crée n'existe pas encore, donc 6.2 ne peut pas être implémentée avant. Aucun changement de contenu à 2.7 elle-même, seulement une dépendance à documenter et respecter dans le séquencement.
- **Epic 5 (Post-vente, Reversements & Rapports)** — non impacté directement. Point de vigilance documenté pour une future itération : des statistiques comparatives inter-éditions (mentionnées par l'utilisateur, ex. "totaux vendus d'une année sur l'autre") s'appuieraient sur les données que la Story 6.2 rend consultables — mais cette capacité comparative est explicitement **hors scope** de la présente proposition (deviendrait une FR-103 / story future, non traitée maintenant).
- Aucune epic n'est invalidée, aucune nouvelle epic n'est nécessaire.

### Conflits d'artefacts

| Artefact | Sections concernées | Nature du changement |
|---|---|---|
| PRD | Nouvelle FR-102 (section Epic 6 / traçabilité des exigences) | Nouvelle exigence |
| Epics/Stories | Nouvelle Story 6.2 après 6.1 ; intro Epic 6 légèrement étendue | Ajout de story, pas de réécriture de l'historique |
| Architecture | Aucun changement de pattern — réutilise l'architecture de filtrage/pagination déjà en place (JPageFlow `FilterService`, `MatPaginator`) sur un nouvel endpoint scopé par `editionId` et lisant la table d'archivage (créée par 2.7) | Aucune section à modifier maintenant — sera précisé lors de l'implémentation de 2.7 puis 6.2 |
| UX | Nouveau composant (sélecteur d'édition archivée + liste réduite) — pas encore spécifié en détail, à faire au moment de la story | Nouvelle interaction, non détaillée ici |
| sprint-status.yaml | Nouvelle entrée `6-2-...` en `backlog` sous Epic 6 | Ajout d'entrée |

### Impact technique

- Backend : nouvel endpoint (ou variante) scopé par `editionId`, lisant la future table d'archivage de la Story 2.7 (schéma : nom, catégorie, statut vendu/invendu par article archivé) — filtrage/tri sur ce sous-ensemble réduit de champs.
- Frontend : nouvelle vue admin-only (sélecteur d'édition archivée + liste filtrable/triable), potentiellement un nouveau composant proche de `ItemCatalogComponent` (6.1) mais adapté au schéma réduit.
- Aucun impact infrastructure/déploiement/CI.

## 3. Approche recommandée

**Option retenue : Ajustement direct (Option 1)**, via une nouvelle story (6.2) dans Epic 6, séquencée explicitement après la Story 2.7.

**Justification** :
- La Story 6.1 reste valide et complète pour son périmètre (édition active) — aucun rollback nécessaire.
- Le nouveau besoin est un ajout ciblé, cohérent avec le pattern d'interaction déjà établi par 6.1, pas une remise en cause du MVP.
- La dépendance sur la Story 2.7 est réelle et incontournable (la donnée source n'existe pas avant) — documentée explicitement plutôt que découverte tardivement pendant l'implémentation de 6.2.
- Le scope est volontairement limité à la consultation par édition (pas de comparaison inter-éditions) suite à un choix explicite de l'utilisateur, pour garder cette première story de taille raisonnable.

**Effort estimé** : Faible à moyen (1 nouvel endpoint scopé par édition + 1 nouvelle vue frontend, réutilisant des patterns déjà établis par 6.1). Ne peut pas démarrer avant que la Story 2.7 soit `done`.
**Risque** : Faible — périmètre bien délimité, aucune remise en cause de code déjà livré.

## 4. Propositions de changement détaillées

### PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`)

**FR-102 (nouvelle)** : L'administrateur peut consulter le catalogue archivé d'une édition passée (déjà Archivée), via un sélecteur d'édition. La liste est filtrable et triable sur les seules données conservées par l'archivage (FR-088) : nom, catégorie, statut vendu/invendu — le prix, le code-barres, la table et le vendeur ne sont plus disponibles après archivage, par construction. Cette consultation sert de brique de base pour de futures statistiques comparatives entre éditions (hors scope de cette exigence — voir FR-103 à venir, non traitée ici). Réservé aux administrateurs.

**Traçabilité des exigences** — nouvelle ligne : *"FR-102 : Epic 6 — Consultation du catalogue archivé d'une édition passée (admin uniquement, dépend de la Story 2.7)"*.

### Epics (`epics.md`)

**Intro Epic 6** — ANCIEN : *"Les administrateurs et les bénévoles peuvent parcourir, rechercher et filtrer tous les articles de l'édition active dans toutes les phases."*
NOUVEAU : *"Les administrateurs et les bénévoles peuvent parcourir, rechercher et filtrer tous les articles de l'édition active dans toutes les phases. Les administrateurs peuvent également consulter le catalogue archivé d'une édition passée."*

**Nouvelle Story 6.2 : Consultation du catalogue d'une édition archivée** (insérée après Story 6.1) :

```
### Story 6.2 : Consultation du catalogue d'une édition archivée

⚠ Dépend de la Story 2.7 (mécanisme d'archivage + table d'archivage) — ne peut pas être implémentée avant.

En tant qu'administrateur,
je veux consulter le catalogue archivé d'une édition passée,
afin de retrouver l'historique d'une édition après sa clôture et son archivage.

**Critères d'acceptation :**

**Étant donné** que l'admin navigue vers la consultation des éditions archivées
**Quand** la page se charge
**Alors** un sélecteur liste toutes les éditions archivées (nom, dates)
**Et** aucun article n'est affiché tant qu'aucune édition n'est sélectionnée

**Étant donné** que l'admin sélectionne une édition archivée
**Quand** la sélection est confirmée
**Alors** la liste des articles archivés de cette édition s'affiche avec pagination (50 par page, MatPaginator), limitée aux données conservées par l'archivage : nom, catégorie, statut vendu/invendu (FR-102)

**Étant donné** que l'utilisateur applique un ou plusieurs filtres
**Quand** les filtres sont soumis
**Alors** la liste se met à jour, filtrée par nom, catégorie, statut vendu/invendu — pas de filtre code-barres/table/vendeur, ces données n'existant plus après archivage (FR-088)

**Étant donné** que l'utilisateur clique sur un en-tête de colonne triable
**Quand** cliqué une fois
**Alors** la liste est triée par ordre croissant avec un indicateur visible
**Et** cliquer à nouveau trie par ordre décroissant

**Étant donné** qu'un bénévole (non admin) tente d'accéder à cette consultation
**Quand** la requête est envoyée
**Alors** l'accès est refusé (403) — réservé aux administrateurs
```

### sprint-status.yaml

Nouvelle entrée sous `development_status`, section Epic 6, après `6-1-catalogue-articles-liste-filtrable-triable: done` :
```yaml
  6-2-consultation-catalogue-edition-archivee: backlog
```
Avec un commentaire documentant la dépendance sur la Story 2.7.

## 5. Implémentation — Handoff

**Scope de changement : Mineur.** Ajout d'artefacts de planification (1 FR, 1 story, 1 entrée sprint-status) sans remise en cause de code déjà livré. Peut être appliqué directement.

**Responsabilités :**
- Artefacts de planification (PRD, epics.md, sprint-status.yaml) : appliqués immédiatement suite à l'approbation de cette proposition.
- Implémentation de la Story 6.2 : à traiter par le Dev agent (`dev-story`), **uniquement après que la Story 2.7 soit `done`**.
- Story 2.7 elle-même : hors scope de cette proposition — déjà planifiée dans Epic 2, non reséquencée ici (reste à sa position actuelle dans le backlog).

**Critères de succès :** FR-102 ajoutée au PRD ; Story 6.2 présente dans `epics.md` avec sa dépendance documentée ; `sprint-status.yaml` reflète la nouvelle entrée `backlog`.
