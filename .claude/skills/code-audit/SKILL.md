---
name: code-audit
description: 'Audit complet du code PluriBourse (front Angular + back Spring Boot) via des agents parallèles indépendants et à scope restreint, un par axe (ou groupe d''axes partageant le même périmètre de fichiers) : qualité front, qualité back, cohérence endpoints front/back, cohérence DTOs/modèles, code mort front, code mort back, clés i18n inutilisées/non traduites, CSS vs UX BMAD, sécurité & données perso dans les logs, calculs financiers BigDecimal, conformité des tests backend, conventions Angular strictes. Utiliser quand l''utilisateur dit "audit de code", "lance l''audit", "audite le code", "vérifie la qualité du code".'
---

# Audit de code PluriBourse

**Objectif :** faire auditer le code par des agents indépendants et à scope restreint, sans qu'ils se marchent dessus ni ne relisent inutilement les mêmes fichiers, puis produire une synthèse unique en français.

**Ton rôle :** orchestrateur. Tu ne fais pas l'audit toi-même — tu prépares le scope, tu avertis l'utilisateur du coût estimé, tu lances les agents en parallèle, tu collectes leurs résultats bruts, puis tu les agrèges en un rapport unique, dédupliqué, trié par sévérité.

## Étape 0 — Avertissement de coût (obligatoire avant de lancer quoi que ce soit)

Cet audit lance plusieurs agents en parallèle qui lisent une bonne partie du code source — ce n'est pas gratuit en tokens/budget. Avant de lancer, à chaque exécution :

1. Détermine le scope retenu (étape 1) et donc la liste des agents qui seront réellement lancés.
2. Calcule un ordre de grandeur avec quelques commandes rapides (`wc -l` sur les dossiers concernés — voir étape 2) pour ne pas donner un chiffre en l'air.
3. Affiche à l'utilisateur, avant de lancer, un message du type :

   > "Cet audit va lancer **N agents** en parallèle (liste : ...). Estimation approximative : **~X tokens** (~Y$ au tarif Sonnet 5 / Haiku 4.5 actuel), soit un ordre de grandeur de **Z%** de ton budget habituel sur 5h si un audit complet t'a déjà coûté 32% par le passé. Je continue ?"

4. **Attends une confirmation explicite** avant de lancer les agents — sauf si l'utilisateur a déjà donné son accord dans le message qui invoque le skill (ex: "lance l'audit complet, vas-y").
5. Si l'utilisateur ne demande pas `all`/tout, rappelle-lui qu'il peut cibler un sous-ensemble d'axes (étape 1) pour réduire le coût.

Repère de calibration connu (à ajuster si l'utilisateur donne un nouveau chiffre) : une exécution complète de la version *non optimisée* du skill (12 agents Sonnet, scope large, pas de fusion) a coûté **32% d'un budget de 5h**. La version actuelle (8 agents, scopes restreints, 4 d'entre eux sur Haiku, fusion des axes qui lisent les mêmes fichiers) est conçue pour diviser ce coût par ~3 — donne cette estimation revue à la baisse, pas le chiffre de 32% tel quel.

## Étape 1 — Déterminer le scope

Par défaut, l'audit porte sur **tout le dépôt**. Si l'utilisateur a invoqué le skill avec un argument, restreint le scope en conséquence :

- Un ou plusieurs mots-clés parmi `back` (qualité + code mort + financier back), `back-tests`, `front` (qualité + code mort + conventions Angular), `i18n`, `css`, `endpoints`, `dto`, `security` → ne lance que les agents correspondants (voir table à l'étape 3).
- `all` ou absence d'argument → lance les 8 agents.
- Une story / un module (ex: "story 3.1", "seller-search") → indique-le comme périmètre prioritaire à **chaque** agent lancé, en précisant qu'il peut quand même chercher dans tout le repo pour vérifier les usages croisés (un code mort ou une clé i18n ne se juge jamais en isolation) — mais que la lecture complète de fichiers doit rester limitée aux fichiers du module quand c'est possible.
- `diff` / "sur la branche" → utilise `git diff main...HEAD --name-only` pour la liste des fichiers modifiés et transmets-la comme périmètre prioritaire à chaque agent concerné.

Ne bloque pas sur une demande de clarification si le scope par défaut (tout le repo) est raisonnable — lance l'audit complet sauf ambiguïté réelle. La confirmation de coût (étape 0) reste obligatoire même quand le scope est évident.

## Étape 2 — Résoudre les chemins clés

Ces chemins sont stables dans ce dépôt — ne les redemande pas à l'utilisateur :

- Backend : `pluribourse-backend/src/main/java/org/pluribourse/`
- Tests backend : `pluribourse-backend/src/test/java/org/pluribourse/`
- Frontend : `pluribourse-frontend/src/app/`
- Fichiers i18n : `pluribourse-frontend/public/i18n/fr.json` et `pluribourse-frontend/public/i18n/en.json`
- Spécifications UX BMAD : `_bmad-output/planning-artifacts/ux-designs/` — s'il y a plusieurs dossiers `ux-PluriBourse-*`, prends le plus récent (tri par date dans le nom) ; le fichier `DESIGN.md` et le sous-dossier `mockups/` de ce dossier sont la référence.
- Conventions de code : `CLAUDE.md` à la racine du repo.
- **Répertoires à exclure systématiquement** de toute lecture ou recherche (build/cache, jamais du code source à auditer) : `node_modules/`, `.angular/`, `dist/`, `target/`, `.git/`, `_bmad/`, `_bmad-output/scripts/`.

Pour l'estimation de coût (étape 0), une commande rapide type `wc -l` sur ces dossiers (en excluant les répertoires ci-dessus) donne un ordre de grandeur du volume à lire.

## Étape 3 — Lancer les agents en parallèle

Lance dans **un seul message**, en parallèle, un appel `Agent` par axe/groupe retenu. **Passe le paramètre `model` indiqué dans la table** — c'est le principal levier de coût : les vérifications structurelles (comparaison de listes, correspondance de champs, présence/absence de clés) ne nécessitent pas un modèle aussi capable que les vérifications de jugement (qualité, sécurité, fidélité visuelle).

**Consignes communes à inclure dans chaque prompt, pour réduire le coût ET éviter les doublons entre agents :**

> "PluriBourse est une plateforme Spring Boot + Angular de gestion de bourses aux jouets. Respecte strictement le périmètre de fichiers indiqué ci-dessous — ne lis et ne parcours QUE ces fichiers, pas le reste de l'application. Exclut toujours `node_modules/`, `.angular/`, `dist/`, `target/`. **Utilise Grep/Glob pour localiser les candidats avant de faire un Read complet** — ne lis un fichier en entier que s'il correspond déjà à un motif pertinent (ex: contient `@RestController`, `HttpClient`, `| translate`, etc.). Ne relis jamais un fichier déjà lu. Limite tes résultats aux constats vérifiés dans le code réel, sois concis dans tes réponses (pas de prose superflue), et ignore tout ce qui relève d'un autre axe listé — un autre agent s'en occupe en parallèle. Si rien à signaler, dis-le explicitement plutôt que d'inventer un problème."

Table des 8 agents (mot-clé → modèle → périmètre exact → sections à produire → exclusions) :

| Mot-clé | Modèle | Périmètre (fichiers à lire, rien d'autre) | Sections produites | Exclusions |
|---|---|---|---|---|
| `back` | `sonnet` (défaut, à ne pas surcharger) | `pluribourse-backend/src/main/java/org/pluribourse/**` uniquement | 1. Qualité code back (couches, MapStruct/Lombok, JavaDoc, duplication, accolades, pas de `var`) — 2. Code mort back (classes/méthodes jamais appelées **en Java**, hors endpoints REST) — 3. Calculs financiers (BigDecimal exclusif dans les packages `seller`/`edition`/tout calcul monétaire, jamais float/double) | `back-tests` (tests), `endpoints` (endpoints jamais appelés par le front) |
| `back-tests` | `haiku` (checklist structurelle) | `pluribourse-backend/src/test/java/org/pluribourse/**` uniquement | Conformité tests : `IntegrationTest`, `@TestMethodOrder`+`@Order`, pas de tests service/repository isolés, pas de Mockito hors composants externes, story-board lisible, cas nominaux+erreur couverts | Qualité du code testé lui-même (`back`) |
| `front` | `sonnet` (défaut) | `pluribourse-frontend/src/app/**` uniquement (jamais `.angular/`, `dist/`, `node_modules/`) | 1. Qualité code front (nommage, duplication, typage, gestion d'erreurs, fuites RxJS/Signals) — 2. Code mort front (composants/services/routes/imports jamais utilisés) — 3. Conventions Angular obligatoires (standalone, Signals seuls, jamais de template inline, accolades systématiques) | Clés i18n (`i18n`), CSS/UX (`css`) |
| `i18n` | `haiku` (comparaison structurelle de listes de clés) | `pluribourse-frontend/public/i18n/fr.json` + `en.json`, puis uniquement les **résultats de Grep** sur `\| translate` / `TranslateService` dans `pluribourse-frontend/src/app/**` (pas de Read complet des composants — grep avec contexte suffit) | Clés définies jamais référencées, chaînes en dur non i18n détectées par grep, clés utilisées absentes de fr/en.json, désynchronisation fr.json/en.json | Qualité générale du code (`front`) |
| `css` | `sonnet` (jugement visuel/design) | Fichiers `*.scss` du frontend (Glob ciblé, pas tout `src/app`) + `DESIGN.md` et `mockups/` du dossier `ux-designs` le plus récent | Couleurs, espacements, typographie, composants du spec vs SCSS réel, écarts avec les mockups | Qualité technique du SCSS en tant que code (`front`) |
| `endpoints` | `haiku` (correspondance structurelle) | Uniquement les fichiers obtenus par Grep : contrôleurs Java contenant `@RestController`/`@*Mapping`, et services Angular (`*.service.ts`) contenant `HttpClient` — ne pas lire le reste des trees front/back | URL/verbe/paramètres concordants, endpoints jamais appelés par le front, appels front vers routes inexistantes | Forme des DTOs (`dto`) |
| `dto` | `haiku` (correspondance structurelle) | Uniquement les fichiers DTO Java (Glob `**/dto/**`) et les fichiers `pluribourse-frontend/src/app/models/**` — pas le reste des trees | Champs (nom/type/nullabilité) concordants, mapping MapStruct cohérent, enums synchronisées | Existence de l'endpoint (`endpoints`) |
| `security` | `sonnet` (jugement risque) | Grep ciblé sur tout le repo (hors exclusions) pour : `log\.(info|debug|warn|error)`, `console\.`, motifs de secrets (`password`, `api[_-]?key`, tokens en dur), concaténation SQL, `innerHTML`/`bypassSecurityTrust` — lire seulement les lignes matchées + quelques lignes de contexte, pas les fichiers entiers | Données perso (nom/email/tel) dans un log, secrets en dur, SQL par concaténation, XSS via innerHTML non justifié | Audit qualité générale |

Chaque prompt d'agent doit se terminer par ce format de sortie imposé :

```
### Résultat — <nom de l'axe ou du groupe>
Périmètre effectivement couvert : ...
## <Section 1>
Constats (0 à N) : [Sévérité: Bloquant|Majeur|Mineur] fichier:ligne — constat — impact — correction proposée
(ou "Aucun problème détecté sur cette section.")
## <Section 2>
...
```

## Étape 4 — Collecter et agréger

Une fois tous les agents revenus :

1. Repère les doublons entre axes (ex: un champ DTO mort peut être signalé à la fois par la section "code mort" de `back` et par `dto`) — fusionne-les en un seul constat, en mentionnant les deux angles.
2. Trie les constats de chaque section par sévérité (Bloquant > Majeur > Mineur).
3. Construis un rapport unique en français :

```
# Rapport d'audit de code — PluriBourse — {date}

## Synthèse
| Axe | Bloquant | Majeur | Mineur |
|---|---|---|---|
| ... une ligne par section effectivement produite ...

## Qualité code back
## Code mort back
## Calculs financiers (BigDecimal)
## Conformité tests backend
## Qualité code front
## Code mort front
## Conventions Angular
## Clés i18n
## CSS vs UX BMAD
## Cohérence endpoints front/back
## Cohérence DTOs/modèles
## Sécurité & données perso dans les logs
(uniquement les sections dont l'agent correspondant a été lancé)

## Recommandations prioritaires
Top 5 constats tous axes confondus, par impact réel sur le produit (pas seulement par sévérité déclarée).
```

4. Affiche ce rapport directement dans la conversation. Ne crée pas de fichier sauf si l'utilisateur le demande explicitement pour cette exécution.

## Notes

- Cette version du skill fusionne les axes qui liraient de toute façon le même arbre de fichiers (ex: qualité front + code mort front + conventions Angular lisent tous `pluribourse-frontend/src/app/**`) pour éviter de payer 3× la même lecture — c'est le levier de coût le plus important, plus important que le choix du modèle.
- 4 des 8 agents (`back-tests`, `i18n`, `endpoints`, `dto`) tournent sur Haiku car ce sont des vérifications structurelles (comparaison de listes, correspondance de champs) qui ne nécessitent pas le jugement de Sonnet — c'est un gain de coût significatif sans perte de qualité sur ce type de tâche.
- Si un agent revient avec un résultat qui déborde du périmètre de fichiers indiqué ou du format demandé, retraite son contenu toi-même en le reclassant dans la bonne section avant d'agréger — ne relance pas l'agent.
- Si l'utilisateur constate que le coût réel diverge significativement de l'estimation de l'étape 0, mets à jour le repère de calibration dans ce fichier (ou demande à l'utilisateur de le faire) pour que les futures estimations restent réalistes.
