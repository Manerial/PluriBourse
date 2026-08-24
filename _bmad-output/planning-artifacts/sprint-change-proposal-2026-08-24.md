# Sprint Change Proposal — 2026-08-24

**Déclencheur :** liste de 8 demandes d'évolution soumises par Manerial sur l'application déjà livrée (toutes les stories des 6 épics sont à `done`, dernier commit "Code review 2"). Il ne s'agit pas de compléter un backlog, mais de faire évoluer un système déjà en production/testé.

**Mode :** revue incrémentale, point par point, avec vérification du code existant avant chaque proposition.

---

## 1. Résumé des problèmes identifiés

Huit demandes, réparties en 4 catégories BMAD :

- **Nouvelle exigence** (jamais spécifiée) : devise par édition (point 1).
- **Évolution du modèle métier** validée après contre-proposition de l'utilisateur : phase de planification (point 2), catégorie du lot (point 3).
- **Bug de cohérence mineur, déjà quasi corrigé en code** : libellés du bordereau de dépôt (point 4).
- **Flux à repenser + gap de robustesse identifié en code** : impression facture (points 5-6), synchronisation du solde (point 7).
- **Reporté** : dark mode (point 8, déjà en mémoire projet V2).

---

## 2. Analyse d'impact par point

### Point 1 — Devise au niveau de l'édition

**Constat code :** aucune notion de devise n'existe (`€` en dur dans `InvoiceRenderer`, `DepositSlipRenderer`, `SettlementReportRenderer`, `ThermalLabelRenderer` + templates frontend). Création complète, pas un déplacement.

**Décision validée :** modélisée sur le patron du taux de commission (FR-016) — valeur par défaut instance, copiée à la création de l'édition, **figée au démarrage du Dépôt**.

**FR :**
```
NOUVEAU FR-103 : Le symbole monétaire est configuré par édition (ex. €, $, CHF), initialisé
  depuis un paramètre instance par défaut à la création de l'édition. Le paramètre instance
  reste modifiable à tout moment et ne s'applique qu'aux nouvelles éditions (même patron que
  FR-006/FR-007 pour la langue de documents).
NOUVEAU FR-104 : La devise d'une édition est modifiable par l'admin jusqu'au démarrage de la
  phase Dépôt, puis figée pour cette édition (même patron que FR-016 pour le taux de commission).
```

**Epic 1 — Story 1.5 (Paramètres admin) :**
```
AVANT : configuration courante affichée : nom de l'association, taux de commission par défaut,
        langue des documents par défaut
APRÈS : + symbole monétaire par défaut (ex. €)
```

**Epic 2 — Story 2.1 (CRUD d'édition) :**
```
AVANT : une nouvelle édition est créée avec ... un taux de commission initialisé depuis le
        paramètre instance ... et une langue de documents initialisée depuis le paramètre instance
APRÈS : + un symbole monétaire initialisé depuis le paramètre instance
AVANT : une édition est entrée en phase Dépôt → tentative de modifier le taux de commission refusée
APRÈS : + tentative de modifier la devise refusée dans les mêmes conditions
```

**Impact technique :** `GlobalInstanceConfig.defaultCurrency` + `Edition.currency` (migration Liquibase), 4 renderers PDF/étiquette backend, templates frontend affichant des prix.

---

### Point 2 — Phase de planification → Préparation non exclusive

**Constat code :** `PhaseType.ACTIVE = List.of(PREPARATION, DEPOSIT, SALE, POST_SALE)` ; `EditionService.createEdition()` refuse toute création tant qu'une édition existe dans cette liste.

**Décision validée (après contre-proposition de l'utilisateur, plus simple qu'une nouvelle phase) :** retirer `PREPARATION` du statut exclusif plutôt que d'introduire une phase "Planification" à part entière. Vérifié : les 15 usages de `getActiveEdition()` dans le code concernent tous des opérations qui ne démarrent qu'à partir du Dépôt (dépôt, catalogue, caisse, solde, rapports) — aucun ne casse.

**FR :**
```
FR-010 — AVANT : Une seule édition peut être active à la fois.
FR-010 — APRÈS : Une seule édition peut être en phase Dépôt, Vente ou Post-vente à la fois.
                  Plusieurs éditions peuvent coexister en phase Préparation.

NOUVEAU FR-105 : Le passage d'une édition de Préparation à Dépôt est refusé si une autre
  édition est déjà en Dépôt, Vente ou Post-vente (contrôle déplacé de la création de
  l'édition — FR-010 actuel — vers cette transition).
```

**Epic 2 — impact :**
- `PhaseType.ACTIVE` : `List.of(PREPARATION, DEPOSIT, SALE, POST_SALE)` → `List.of(DEPOSIT, SALE, POST_SALE)`.
- Story 2.1 : suppression du contrôle d'unicité à la création.
- Story 2.2 (cycle de phases) : nouveau contrôle sur la transition Préparation→Dépôt.

---

### Point 3 — Catégorie du lot

**Constat code :** la catégorie est portée par `Item`, y compris pour les membres d'un lot (conforme à l'AC actuel de la Story 3.3). `LotService` a une logique substantielle et testée de verrouillage pessimiste par catégorie et de réassignation de table individuelle par article (Story 3.10). Rien n'empêche aujourd'hui deux articles du même lot d'être sur des tables différentes.

**Données existantes :** pas d'édition réelle en cours — migration simple, pas de réconciliation de données à prévoir.

**FR :**
```
FR-022 — AVANT : Pour chaque article (individuel ou membre d'un lot), le bénévole saisit :
  nom/description, prix, catégorie, indicateur complet/incomplet, commentaire.
FR-022 — APRÈS : Pour un article individuel : nom/description, prix, catégorie, indicateur
  complet/incomplet, commentaire. Pour un lot : la catégorie est saisie une fois pour
  l'ensemble du lot ; chaque article du lot saisit nom/description, indicateur
  complet/incomplet et commentaire (pas de catégorie individuelle).
FR-023 — précision : pour un lot, l'assignation de table s'effectue une seule fois à partir
  de la catégorie du lot ; tous les articles du lot partagent la même table.
```

**Epic 3 — impact :**
- `Lot` : nouveau champ `category` (migration). Retrait de `Item.category` pour les membres d'un lot.
- Story 3.3 : un seul sélecteur de catégorie pour le lot ; table assignée une fois pour le lot entier.
- Story 3.10 : la réassignation de catégorie devient une opération sur le lot entier — **retouche du cœur du verrouillage pessimiste par catégorie dans `LotService`**, pas un simple renommage de champ.
- `ThermalLabelRenderer` : source de la catégorie affichée passe de `item.getCategory()` à `lot.getCategory()`.

---

### Point 4 — Bordereau de dépôt : total avant commission + "reversement max"

**Constat code : déjà implémenté.** `DepositSlipRenderer.java` utilise déjà `print.slip.totalGross` et `messages_fr.properties`/`messages_en.properties` disent déjà "Reversement max"/"Max payout" (commit "Code review 2"). Seul le bundle de secours `messages.properties` (fallback sans suffixe de locale) est resté à l'ancien texte et n'a pas la clé `totalGross` — risque de `NoSuchMessageException` si jamais atteint.

**Ce n'est pas un changement de sprint** — traité comme correctif direct hors story, pas d'impact PRD/epic :
```
messages.properties — AVANT :
print.slip.commission=Commission rate: {0}%
print.slip.netAmount=Net payout: {0}€

messages.properties — APRÈS :
print.slip.totalGross=Total before commission: {0}€
print.slip.commission=Commission rate: {0}%
print.slip.netAmount=Max payout: {0}€
```

---

### Points 5 & 6 — Impression de la facture acheteur (checkbox à la vente + réimpression)

**Constat code :** le bouton "Imprimer la facture" n'est visible que 30 secondes après la vente (`INVOICE_BUTTON_VISIBLE_MS = 30000` dans `pos-page.component.ts`), et `PosInvoicePrintService` interdit l'accès à quiconque n'est pas l'auteur de la vente. Au-delà, aucun écran ne permet de retrouver une vente passée (achats anonymes, pas de compte acheteur).

**Décisions validées :**
- Case à cocher "Imprimer la facture" dans le dialogue de validation du paiement, **cochée par défaut**.
- Réimpression ouverte à **tout bénévole caissier** (retrait de la restriction "auteur de la vente uniquement").
- Nouvel écran **Liste des ventes**, filtrable par date/heure et poste, avec action "Réimprimer" par ligne.
- Le bouton temporaire à 30s sur l'écran caisse est **supprimé** (devenu redondant).

**FR :**
```
NOUVEAU FR-107 : Lors de la validation du paiement, le caissier peut cocher « Imprimer la
  facture » (cochée par défaut) pour déclencher automatiquement l'impression de la facture
  acheteur à la validation, sans clic supplémentaire après coup.

NOUVEAU FR-108 : Toute vente de l'édition active est accessible depuis un écran « Liste des
  ventes », filtrable par date/heure et poste de caisse. Chaque ligne propose une action
  « Réimprimer la facture », accessible à tout bénévole caissier.

FR-040 — AMENDÉ : Après validation, une facture acheteur est imprimable à la demande
  (immédiatement, via la case à cocher FR-107, ou ultérieurement depuis la Liste des ventes,
  FR-108). Le bouton d'impression temporaire limité à 30s sur l'écran caisse (Story 4.5) est
  supprimé.
```

**Epic 4 — impact :**
- Story 4.2 (validation du paiement) : nouvelle case à cocher dans le dialogue de confirmation.
- Story 4.5 (impression facture) : AC "fenêtre 30s" retiré ; restriction "auteur uniquement" retirée de `PosInvoicePrintService`.
- `pos-page.component.ts` : suppression de `lastSale`/`invoiceButtonTimer`/bouton temporaire.
- **Nouvelle story** : écran "Liste des ventes" (nouveau composant Angular, nouvel endpoint backend liste + filtre, action réimpression) — pattern liste filtrable déjà utilisé pour catalogue/vendeurs (UX-DR11) réutilisable comme référence.

---

### Point 7 — Synchronisation des postes de soldage

**Constat code :** `Settlement.seller_profile_id` a une contrainte UNIQUE en base (filet de sécurité), mais `SettlementService.settle()` fait un contrôle "vérifier puis agir" sans verrou applicatif — contrairement au verrouillage optimiste `@Version` imposé sur `Item` pour la caisse (NFR-002/ARCH-003, retour 409 propre). Aucune synchronisation temps réel de la liste `/volunteer/settlement` entre postes — contrairement au chip de phase (SSE, ARCH-012).

**NFR / ARCH :**
```
NOUVEAU NFR-008 : Le système empêche le double-traitement (solde ou non-réclamé) d'un même
  vendeur depuis deux postes de soldage simultanés. Le second poste reçoit une erreur 409
  explicite (même patron que NFR-002 pour la caisse), pas une erreur générique.
NOUVEAU ARCH-017 : SSE dédié aux mises à jour de la liste de solde (même patron que
  SseEmitterRegistry/ARCH-012) : événement émis à chaque solde/non-réclamé, la liste
  `/volunteer/settlement` et `/admin/settlement` se met à jour en temps réel chez tous les
  postes connectés sans rechargement de page.
```

**Epic 5 — impact :**
- Story 5.1 : nouveaux CA — conflit de solde concurrent → 409 explicite ; liste mise à jour en temps réel via SSE.
- Contrôle applicatif explicite à ajouter sur le modèle de `PosBasketService`/ARCH-003.
- Nouveau canal SSE dédié.

---

### Point 8 — Dark mode

Aucune action — déjà catalogué en mémoire projet comme reporté V2, confirmé sans changement.

---

## 3. Approche recommandée

**Ajustement direct** (Option 1 du checklist correct-course) pour l'ensemble des points : aucun rollback nécessaire, le MVP livré n'est pas remis en cause, chaque point est une évolution incrémentale des épics existants. Aucun point ne nécessite de revoir le périmètre MVP du PRD.

Le point 4 est traité en dehors du présent processus de sprint change (correctif direct, pas une évolution de spécification).

---

## 4. Impact MVP et plan d'action

Le MVP livré (6 épics, toutes stories `done`) n'est pas remis en cause. Plan d'action :

| Ordre suggéré | Points | Dépendances |
|---|---|---|
| 1 | Point 4 (correctif direct) | Aucune — peut être fait immédiatement |
| 2 | Point 2 (Préparation non exclusive) | Aucune — modifie la machine d'état de base (Epic 2), prérequis conceptuel avant de retoucher les points 1 et 3 qui touchent aussi Edition/Item |
| 3 | Point 1 (devise édition) | Bénéficie d'être fait après le point 2 (même zone Edition) |
| 4 | Point 3 (catégorie du lot) | Aucune dépendance avec les autres, mais retouche `LotService` en profondeur — à isoler dans son propre cycle de dev/test |
| 5 | Points 5 + 6 (facture) | Aucune — forment une seule story cohérente Epic 4 |
| 6 | Point 7 (synchro soldage) | Aucune — indépendant |

## 5. Plan de transmission (handoff)

| Point | Ampleur | Transmis à |
|---|---|---|
| 4 | Mineure | Développeur — correctif direct, pas de story |
| 1 | Modérée | PO/Dev — nouvelle story (bmad-create-story) |
| 2 | Modérée (machine d'état partagée) | PO/Dev — nouvelle story, revue attentive recommandée vu l'impact transverse |
| 3 | Modérée (retouche logique de verrouillage existante) | PO/Dev — nouvelle story |
| 5+6 | Modérée à majeure (nouvel écran) | PO/Dev pour la case à cocher ; UX pour l'écran "Liste des ventes" avant chiffrage dev |
| 7 | Modérée à majeure (nouveau canal SSE + verrouillage) | PO/Dev — nouvelle story |

**Succès :** chaque point donne lieu à une story dédiée créée via `bmad-create-story`, sauf le point 4 (correctif direct) et le point 8 (aucune action).
