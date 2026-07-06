---
date: 2026-07-06
author: Manerial (via Claude Code — bmad-correct-course)
status: approuvé
---

# Sprint Change Proposal — Retrait du blocage de connexion bénévole (FR-099)

## 1. Résumé du problème

**Story déclenchante :** Story 2.3 « Blocage de connexion bénévole sans édition active » (terminée le 2026-06-30, statut `done`), qui a introduit FR-099.

**Nature du problème :** redondance découverte a posteriori. FR-099 bloque la connexion des bénévoles lorsqu'aucune édition n'est en phase active (Préparation, Dépôt, Vente, Post-vente). Une analyse menée le 2026-07-06, dans le cadre d'une revue de plusieurs anomalies signalées par l'utilisateur, a établi que cette protection n'apporte aucune garantie supplémentaire : l'isolation et la protection des données d'édition (FR-015) sont déjà assurées nativement, à la couche métier, à **chaque requête** — indépendamment de l'état de la session HTTP.

**Preuves rassemblées :**
- `ItemService` et `SellerService` appellent `EditionService.getActiveEdition()` à chaque opération (y compris les lectures), qui lève `NoActiveEditionException` (404) si aucune édition n'est active. Aucune mutation ou consultation de données métier n'est possible sans édition active, que le bénévole soit connecté ou non.
- La page `/account` (préférence de langue) reste accessible sans édition active — déjà toléré aujourd'hui, sans risque : c'est un réglage personnel, sans impact sur les données de l'événement.
- La revue de code de la Story 2.3 (30/06/2026) avait déjà identifié et accepté un effet de bord du blocage : le 401 `no-active-edition` (distinct du 401 `invalid-credentials`) révèle si des identifiants sont valides même hors saison — un « timing oracle » alors classé comme risque accepté. Le retrait du blocage supprime ce signal.
- Le timeout de session (`spring.session.timeout=PT1H`) borne déjà toute session résiduelle, avec ou sans ce blocage.

**Bénéfice attendu du retrait :** un bénévole peut se connecter à tout moment, y compris entre deux événements, pour par exemple ajuster sa préférence de langue à l'avance — sans que cela n'ouvre le moindre accès aux données d'une édition.

## 2. Analyse d'impact

### Impact sur les epics
- Epic 2 (Gestion des éditions) reste valide dans son ensemble ; seule la Story 2.3 est concernée.
- Aucune story future ne dépend de FR-099 ou du comportement `no-active-edition` côté connexion (vérifié par recherche exhaustive dans `epics.md`).
- Aucun resséquencement d'epic nécessaire.

### Conflits avec les artefacts
- **PRD** (`prd.md:161`) : FR-099 à amender — la clause de blocage de connexion est retirée, remplacée par un renvoi vers FR-015 comme mécanisme de protection réel.
- **Epics** (`epics.md`) : FR-099 (ligne 48), table de couverture Epic 2 (ligne 226), et section Story 2.3 (lignes 828-855) à amender.
- **Architecture** : aucun impact — le mécanisme Spring Session JDBC n'est pas concerné par ce changement.
- **UX** : aucune spec UX dédiée trouvée pour l'écran d'erreur `no-active-edition` ; impact nul à négligeable.
- **Tests** : `VolunteerEditionGateIT.java` (backend) et `login.component.spec.ts` (frontend) référencent directement le comportement retiré.
- **i18n** : clé `auth.login.error.no-active-edition` (en/fr) devient orpheline.

## 3. Approche retenue

**Option 1 — Ajustement direct.** Retrait net de code et de spécification, sans remplacement par une nouvelle mécanique. Effort : faible. Risque : faible (suppression de logique, pas d'ajout de complexité ; la protection réelle des données — FR-015 — n'est pas touchée).

Les options 2 (rollback complet de la Story 2.3) et 3 (révision du MVP) ont été écartées : la story dans son ensemble reste valide (elle a aussi livré la notion d'édition active exploitée par FR-015), seul son garde-fou de connexion est concerné.

## 4. Propositions de changement détaillées

### 4.1 — PRD (`_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md:161`)

**AVANT :**
> FR-099 | Si aucune édition n'est en phase active (Préparation, Dépôt, Vente ou Post-vente), les bénévoles ne peuvent pas se connecter. L'authentification est rejetée avec HTTP 401 et un type d'erreur distinct `no-active-edition`. La page de connexion affiche un message spécifique. Les administrateurs ne sont pas affectés par cette restriction. Les sessions bénévoles déjà ouvertes restent valides jusqu'à déconnexion.

**APRÈS :**
> FR-099 | *(Retiré — 2026-07-06, voir `sprint-change-proposal-2026-07-06.md`)* Les bénévoles peuvent se connecter à tout moment, y compris hors édition active. L'accès aux données d'une édition reste strictement conditionné à l'existence d'une édition active et à sa phase courante, vérifié côté serveur à chaque requête métier (voir FR-015).

### 4.2 — Epics (`_bmad-output/planning-artifacts/epics.md`)

**a) Ligne 48 :**
**AVANT :** `FR-099 : Si aucune édition n'est en phase active (Préparation, Dépôt, Vente, Post-vente), les bénévoles ne peuvent pas se connecter. L'authentification est rejetée avec une erreur distinguable no-active-edition. Les administrateurs ne sont pas affectés. Les sessions bénévoles déjà ouvertes restent valides jusqu'à déconnexion.`
**APRÈS :** `FR-099 : (Retiré — 2026-07-06) Les bénévoles peuvent se connecter à tout moment ; l'accès aux données d'édition reste protégé par FR-015 (isolation stricte par édition), vérifiée côté serveur à chaque requête.`

**b) Ligne 226 :**
**AVANT :** `FR-099 : Epic 2 — Blocage de connexion bénévole sans édition active (Story 2.3)`
**APRÈS :** `FR-099 : Epic 2 — Retiré (voir Story 2.3, amendée le 2026-07-06)`

**c) Section Story 2.3 (lignes 828-855)** — remplacée intégralement par :
```
### Story 2.3 : Blocage de connexion bénévole sans édition active — RETIRÉE

**Statut : amendée le 2026-07-06** (voir sprint-change-proposal-2026-07-06.md).

Le blocage de connexion des bénévoles hors édition active a été retiré : l'analyse a montré
qu'il n'apportait pas de protection propre, celle-ci étant déjà assurée par la vérification
systématique de l'édition active et de sa phase à chaque requête métier (FR-015). Les bénévoles
peuvent désormais se connecter à tout moment. Voir l'historique complet dans
2-3-blocage-benevoles-sans-edition-active.md.
```

### 4.3 — Story historique (`_bmad-output/implementation-artifacts/2-3-blocage-benevoles-sans-edition-active.md`)

- Ligne 7 : `Status: done` → `Status: amended (2026-07-06 — see sprint-change-proposal-2026-07-06.md)`
- Ajout en fin de Change Log :
```
- 2026-07-06: Story amendée — le blocage de connexion bénévole (FR-099) est retiré. L'analyse a
  montré que la protection était déjà assurée nativement par la vérification systématique de
  l'édition active/phase à chaque requête métier (ItemService/SellerService), rendant le blocage
  de connexion redondant et générateur de friction UX inutile entre deux événements. Voir
  sprint-change-proposal-2026-07-06.md pour l'analyse complète. Code retiré :
  LoginSuccessHandler.java (gate ACTIVE_PHASES), VolunteerEditionGateIT.java,
  login.component.ts (branche d'erreur no-active-edition), clés i18n associées.
```

### 4.4 — Code

- **`LoginSuccessHandler.java`** : retrait du bloc de garde `if (Role.VOLUNTEER... && !editionRepository.existsByPhaseIn(...))` et de son contenu (SecurityContext clear, invalidation de session, réponse 401 `no-active-edition`) ; retrait du champ `EditionRepository editionRepository` et des imports `org.pluribourse.edition.entity.*` / `org.pluribourse.edition.repository.*` devenus inutiles.
- **`VolunteerEditionGateIT.java`** : suppression complète du fichier.
- **`login.component.ts`** : retrait de `'no-active-edition'` du type de l'union `error`, retrait de la branche `errorType === '.../no-active-edition'` dans le `catch`.
- **`login.component.spec.ts`** : suppression du test `'sets error to no-active-edition when backend returns that error type'`.
- **`en.json` / `fr.json`** : suppression de la clé `auth.login.error.no-active-edition`.

## 5. Plan de handoff

**Portée : Mineure** — implémentation directe, pas de réorganisation de backlog ni de replanification stratégique.

**Responsable :** agent développeur (Claude Code, dans la foulée de cette session).

**Critères de succès :**
- `mvn test` (backend) et `npm test` (frontend) passent sans régression après retrait du code.
- Un bénévole peut se connecter avec succès même en l'absence d'édition active (vérifié manuellement ou via test de non-régression existant).
- Aucune référence résiduelle à `no-active-edition` côté connexion (grep de contrôle sur le code et les i18n).
- PRD, epics.md et la story historique 2.3 reflètent le nouvel état.

## 6. Approbation

Approuvé par Manerial le 2026-07-06, en mode incrémental — chaque proposition (4.1 à 4.4) validée individuellement avant compilation de ce document.
