---
stepsCompleted: [1, 2, 3, 4, 5, 6]
documents:
  prd:
    - _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md
    - _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md
  architecture:
    - _bmad-output/planning-artifacts/architecture.md
  epics:
    - _bmad-output/planning-artifacts/epics.md
  ux:
    - _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md
    - _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md
---

# Rapport d'Évaluation de Prêt-à-Implémenter

**Date :** 2026-06-09
**Projet :** PluriBourse

---

## Analyse PRD

### Exigences Fonctionnelles

**F1 — Internationalisation (EN/FR)** — 7 FRs
- FR-001 : L'interface utilisateur est disponible en anglais et en français.
- FR-002 : La langue par défaut est détectée depuis le navigateur au premier accès et stockée dans les préférences du compte.
- FR-003 : Chaque utilisateur peut modifier sa préférence de langue dans les paramètres du compte.
- FR-004 : Tout le texte de l'interface est externalisé — aucun texte codé en dur dans le code source.
- FR-005 : La langue de tous les documents imprimés est configurée au niveau de l'instance par l'admin.
- FR-006 : Le paramètre de langue des documents s'applique à toute l'instance et à toutes les éditions.
- FR-007 : Le paramètre de langue des documents est modifiable par l'admin à tout moment.

**F2 — Gestion des Éditions & Cycle de Vie** — 12 FRs
- FR-008 : L'admin peut créer une édition avec un nom libre.
- FR-009 : Plusieurs éditions peuvent être créées par an.
- FR-010 : Une seule édition peut être active à la fois.
- FR-011 : Toute transition de phase nécessite une confirmation explicite de l'admin.
- FR-012 : La phase active est affichée clairement à tous les utilisateurs connectés.
- FR-013 : L'admin clôture l'édition via un bouton dédié ; tous les PDFs sont générés ; l'édition passe en lecture seule.
- FR-014 : Une édition archivée ne peut pas être supprimée.
- FR-015 : Les données de chaque édition sont strictement cloisonnées.
- FR-016 : Le taux de commission est configurable jusqu'au démarrage de la phase Dépôt, puis gelé pour l'édition.
- FR-080 : Lors de la création d'une édition, l'admin peut copier la structure d'une édition existante.
- FR-082 : L'admin peut revenir en arrière d'une phase à la fois ; les données sont toujours préservées.
- FR-088 : Après clôture, l'admin peut déclencher « Nettoyer l'Édition » (suppression définitive des articles, désactivation du rollback).

**F3 — Gestion des Vendeurs & Articles (Phase de Dépôt)** — 19 FRs
- FR-017 : L'admin configure la liste des catégories d'articles par édition.
- FR-018 : L'admin configure le mapping catégorie-table par édition.
- FR-019 : Les profils vendeurs persistent inter-éditions (nom, prénom, email, téléphone).
- FR-020 : Le bénévole recherche un vendeur existant par nom ou email ; si absent, création d'un nouveau profil.
- FR-021 : L'admin peut supprimer un profil vendeur (RGPD — anonymisation dans toutes les éditions).
- FR-022 : Pour chaque article : nom/description, prix, catégorie, indicateur complet/incomplet, commentaire si incomplet.
- FR-023 : La table est assignée automatiquement selon le mapping catégorie-table de l'édition.
- FR-024 : Un article ne peut être corrigé ou supprimé qu'en phase de Dépôt.
- FR-025 : L'indicateur complet/incomplet et son commentaire sont modifiables dans toutes les phases.
- FR-026 : Un code-barres Code 128 unique est généré côté serveur pour chaque article inscrit.
- FR-027 : L'étiquette affiche : code-barres, numéro de code-barres, nom de l'article, prix, catégorie, numéro de table, indicateur incomplet (pas de nom vendeur — RGPD).
- FR-028 : L'impression des étiquettes est déclenchée automatiquement à la validation du dépôt d'un vendeur.
- FR-029 : Les travaux d'impression sont mis en file d'attente côté serveur, exécution séquentielle.
- FR-030 : Format du rouleau : [séparateur vendeur] → [étiquette article] → [séparateur article] → ...
- FR-031 : Un bordereau de dépôt est imprimable par vendeur (articles, prix unitaires, reversement net attendu).
- FR-032 : La largeur du ticket thermique est configurable dans les paramètres admin (défaut : 57 mm).
- FR-043 : Un bénévole peut créer un lot (nom, prix global, plusieurs articles).
- FR-044 : Chaque article du lot possède son propre nom/description et sa propre étiquette.
- FR-045 : L'étiquette d'un article de lot affiche « Prix du lot : X€ » et « Lot indivisible : X/N ».

**F4 — Point de Vente (Phase de Vente)** — 14 FRs
- FR-033 : L'interface caisse permet les ventes via scanner USB HID.
- FR-034 : Le composant de scan gère AZERTY/QWERTY de façon transparente via mappage de codes touches.
- FR-035 : Chaque article scanné est ajouté au panier ; le système affiche le nom et le prix.
- FR-036 : Scanner un article déjà vendu affiche un message d'erreur explicite ; l'article n'est pas ajouté.
- FR-037 : Scanner un article incomplet affiche un avertissement avec détail de ce qui manque ; l'article peut être vendu.
- FR-038 : Le caissier peut retirer des articles individuels du panier avant validation.
- FR-039 : La validation marque tous les articles comme vendus ; pas de retour ni d'échange possible.
- FR-040 : Après validation, une facture acheteur est imprimable à la demande.
- FR-041 : La facture affiche : articles, prix unitaires, total, nom association, nom édition, date. Un lot = une ligne.
- FR-042 : Minimum 3 postes caisse simultanés sans conflits de données.
- FR-046 : Scanner un article de lot affiche le nom du lot en rouge avec compteur X/N.
- FR-047 : La validation est bloquée tant que le lot n'est pas complet.
- FR-048 : Le lot complet est vendu à son prix global.
- FR-081 : Le caissier peut retirer un lot entier du panier (si lot incomplet).

**F5 — Post-Vente & Reversements** — 5 FRs
- FR-049 : En phase Post-vente, un bilan de vente est imprimable par vendeur.
- FR-050 : Le bilan contient : articles vendus, invendus (avec table), total brut, commission déduite, montant net. Un lot = une ligne.
- FR-051 : Le bénévole saisit le montant remis en espèces et clique « Reverser » ; le statut passe à Reversé.
- FR-052 : Bouton « Non collecté » : le montant intégral est enregistré comme recette de l'association.
- FR-053 : Les vendeurs non reversés sont identifiables, avec leur numéro de téléphone visible.

**F6 — Rapports** — 6 FRs
- FR-054 : Bilan journalier générable par l'admin pendant la phase Vente (jour courant).
- FR-055 : Bilan d'édition généré à la clôture (totaux vendus/invendus, chiffre d'affaires, commission).
- FR-056 : Rapport des vendeurs en attente (non reversés, avec numéro de téléphone).
- FR-057 : Tous les rapports sont générés en PDF.
- FR-058 : Rapports accessibles à l'admin uniquement.
- FR-059 : Éditions archivées : métriques agrégées et profils vendeurs en lecture seule ; détail article uniquement via PDF.

**F7 — Comptes Utilisateurs & Contrôle d'Accès** — 8 FRs
- FR-060 : L'admin crée, modifie, désactive les comptes bénévoles ; peut réinitialiser les mots de passe.
- FR-061 : Un seul compte admin par instance.
- FR-062 : Premier lancement : Admin/Admin, changement forcé à la première connexion.
- FR-063 : Reset mot de passe admin via commande serveur ; changement forcé à la reconnexion.
- FR-064 : Admin et Bénévole strictement séparés.
- FR-065 : L'interface bénévole s'adapte à la phase active (dépôt / vente / post-vente / catalogue).
- FR-066 : Les sessions n'expirent pas automatiquement.
- FR-067 : Chaque compte stocke une préférence de langue (EN/FR), modifiable dans les paramètres.

**F8 — Infrastructure & Déploiement** — 7 FRs
- FR-068 : Le serveur fonctionne sous Linux, macOS et Windows sans modification du code.
- FR-069 : Spec minimale : Raspberry Pi 4 (2 Go RAM) ou machine 64 bits équivalente ; SSD/USB recommandé.
- FR-070 : Déploiement via Docker Compose (Spring Boot + MariaDB) ; données dans volumes persistants.
- FR-071 : Mises à jour : `docker compose pull && docker compose up -d` ; données préservées.
- FR-072 : Postes clients : accès via navigateur, aucune installation locale requise.
- FR-073 : Page Paramètres admin : nom association, taux commission, langue documents, largeur ticket.
- FR-074 : Guide d'installation exhaustif pour non-techniciens (Docker, démarrage, config, reset, mises à jour ; par OS).

**F9 — Infrastructure d'Impression** — 5 FRs
- FR-075 : Toute impression est routée via le serveur central ; aucune imprimante requise sur les postes clients.
- FR-076 : Imprimante thermique (étiquettes) connectée au serveur via USB ; voir FR-032 pour la largeur, FR-029 pour la file.
- FR-077 : Imprimante standard (documents A4) connectée au serveur via USB ; PDF envoyé directement sans aperçu.
- FR-078 : Le déclenchement d'impression est côté serveur ; aucune action requise sur le poste client.
- FR-079 : En cas d'erreur d'impression, l'utilisateur est notifié dans l'interface avec un message explicite.

**F10 — Catalogue Articles** — 7 FRs
- FR-083 : Catalogue filtrable et triable accessible à l'admin et aux bénévoles pendant toutes les phases de l'édition active.
- FR-084 : Filtres : nom/description, numéro de code-barres, catégorie, table, vendu/invendu, complet/incomplet, nom vendeur.
- FR-085 : Tri par n'importe quelle colonne visible.
- FR-086 : Catalogue : édition active uniquement ; données indisponibles après Nettoyer.
- FR-087 : En phase Vente, ajout direct d'un article du catalogue au panier courant (repli code-barres illisible) ; empêche les doublons.
- FR-089 : La commission s'applique normalement aux articles incomplets vendus.
- FR-090 : Transition de phase avec panier actif : le panier est annulé et un message d'erreur est affiché au bénévole.

**Total FRs : 90** (FR-001 à FR-090 ; quelques numéros non utilisés dans la séquence : FR-082 et FR-088 dans F2, FR-081 dans F4, FR-080 dans F2 — voir carte de couverture FR dans epics.md)

---

### Exigences Non Fonctionnelles

- NFR-001 (Performance) : Utilisable sur Raspberry Pi 4 (2 Go RAM) sans dégradation sous charge événementielle (3 postes, ~1 700 articles).
- NFR-002 (Concurrence) : Opérations simultanées depuis plusieurs postes sans conflits de données.
- NFR-003 (Exactitude Financière) : Calculs de reversement (prix − commission) exacts au centime.
- NFR-004 (Compatibilité Navigateur) : Fonctionne sur tout navigateur moderne (Chrome, Firefox, Edge, Safari) sur tout OS.
- NFR-005 (Compatibilité Scanner) : Scanners USB HID fonctionnent sans configuration, quelle que soit la disposition clavier.
- NFR-006 (Fiabilité) : Aucune perte de données sur fermeture inattendue du navigateur ou défaillance de poste.
- NFR-007 (RGPD) : Données personnelles vendeur supprimables sur demande ; anonymisation n'autorisant pas la réidentification.

**Total NFRs : 7**

---

### Exigences Additionnelles (Addendum)

- **Infrastructure** : Docker Compose + MariaDB ; volumes persistants ; mises à jour en 2 commandes.
- **Architecture i18n** : Frontend ngx-translate (JSON) + Backend Spring MessageSource (.properties) ; fichiers séparés.
- **Impression thermique** : ESC/POS sur rouleau 57 mm via USB serveur ; bibliothèque Java `escpos-coffee` ou équivalente ; file d'impression séquentielle côté serveur.
- **Sauvegarde** : Hors scope v1.

---

### Évaluation de Complétude du PRD

Le PRD est **complet et bien structuré** :
- 90 FRs couvrant 10 groupes fonctionnels (F1–F10)
- 7 NFRs couvrant les contraintes critiques (performance, concurrence, financier, RGPD)
- Numérotation cohérente (quelques lacunes volontaires dans la séquence ; toutes les lacunes proviennent de FRs numérotés hors séquence tels FR-080, FR-081, FR-082, FR-088–090)
- Addendum complète utilement les décisions techniques non couvertes par les FRs
- Les métriques de succès (SM-1 à SM-7) fournissent des critères de validation mesurables

---

## Validation de Couverture des Épics

### Matrice de Couverture FR

| FR | Exigence (résumé) | Épic | Statut |
|----|-------------------|------|--------|
| FR-001 | Interface disponible en EN et FR | Épic 1 | ✓ Couvert |
| FR-002 | Langue détectée depuis le navigateur → préférence utilisateur | Épic 1 | ✓ Couvert |
| FR-003 | L'utilisateur peut modifier sa préférence de langue | Épic 1 | ✓ Couvert |
| FR-004 | Tous les textes externalisés, aucune chaîne codée en dur | Épic 1 | ✓ Couvert |
| FR-005 | Langue des documents configurée au niveau de l'instance | Épic 1 | ✓ Couvert |
| FR-006 | Langue des documents applicable à toute l'instance | Épic 1 | ✓ Couvert |
| FR-007 | Langue des documents modifiable par l'admin | Épic 1 | ✓ Couvert |
| FR-008 | L'admin crée une édition avec un nom libre | Épic 2 | ✓ Couvert |
| FR-009 | Plusieurs éditions par an | Épic 2 | ✓ Couvert |
| FR-010 | Une seule édition active à la fois | Épic 2 | ✓ Couvert |
| FR-011 | Transition de phase nécessite une confirmation explicite | Épic 2 | ✓ Couvert |
| FR-012 | Phase active affichée à tous les utilisateurs | Épic 2 | ✓ Couvert |
| FR-013 | Clôture de l'édition : génère PDFs, passe en lecture seule | Épic 2 | ✓ Couvert |
| FR-014 | Édition archivée ne peut pas être supprimée | Épic 2 | ✓ Couvert |
| FR-015 | Données de chaque édition strictement isolées | Épic 2 | ✓ Couvert |
| FR-016 | Taux de commission figé au démarrage de la phase Dépôt | Épic 2 | ✓ Couvert |
| FR-017 | L'admin configure les catégories d'articles par édition | Épic 3 | ✓ Couvert |
| FR-018 | L'admin configure le mapping catégorie-table | Épic 3 | ✓ Couvert |
| FR-019 | Profils vendeurs persistent d'une édition à l'autre | Épic 3 | ✓ Couvert |
| FR-020 | Le bénévole recherche/crée des profils vendeurs | Épic 3 | ✓ Couvert |
| FR-021 | L'admin peut supprimer un profil vendeur (RGPD) | Épic 3 | ✓ Couvert |
| FR-022 | Le bénévole saisit les détails de l'article | Épic 3 | ✓ Couvert |
| FR-023 | Table auto-assignée selon le mapping de catégorie | Épic 3 | ✓ Couvert |
| FR-024 | Article corrigeable/supprimable uniquement en phase Dépôt | Épic 3 | ✓ Couvert |
| FR-025 | Indicateur complet/incomplet modifiable dans toutes les phases | Épic 3 | ✓ Couvert |
| FR-026 | Code-barres Code 128 généré côté serveur par article | Épic 3 | ✓ Couvert |
| FR-027 | Format étiquette article (code-barres, nom, prix, catégorie, table, incomplétude) | Épic 3 | ✓ Couvert |
| FR-028 | Étiquettes imprimées automatiquement à la validation du dépôt | Épic 3 | ✓ Couvert |
| FR-029 | Travaux d'impression mis en file d'attente séquentiellement | Épic 3 | ✓ Couvert |
| FR-030 | Format du rouleau thermique : séparateur vendeur → étiquettes | Épic 3 | ✓ Couvert |
| FR-031 | Bordereau de dépôt imprimable par vendeur | Épic 3 | ✓ Couvert |
| FR-032 | Largeur du ticket thermique configurable (défaut 57 mm) | Épic 3 | ✓ Couvert |
| FR-033 | Interface caissier avec scanner USB HID | Épic 4 | ✓ Couvert |
| FR-034 | Gestion transparente AZERTY/QWERTY | Épic 4 | ✓ Couvert |
| FR-035 | Article scanné ajouté au panier avec nom et prix | Épic 4 | ✓ Couvert |
| FR-036 | Scan article déjà vendu : message d'erreur, non ajouté | Épic 4 | ✓ Couvert |
| FR-037 | Scan article incomplet : avertissement, toujours vendable | Épic 4 | ✓ Couvert |
| FR-038 | Le caissier peut retirer des articles du panier avant validation | Épic 4 | ✓ Couvert |
| FR-039 | La validation marque les articles vendus, clôt la transaction | Épic 4 | ✓ Couvert |
| FR-040 | Facture acheteur imprimable à la demande après validation | Épic 4 | ✓ Couvert |
| FR-041 | Format de la facture : liste articles, prix, total, association, édition, date | Épic 4 | ✓ Couvert |
| FR-042 | Minimum 3 postes caissiers simultanés sans conflits | Épic 4 | ✓ Couvert |
| FR-043 | Le bénévole peut créer un lot avec un prix global + plusieurs articles | Épic 3 | ✓ Couvert |
| FR-044 | Chaque article du lot a son propre nom et étiquette | Épic 3 | ✓ Couvert |
| FR-045 | Étiquette d'un article de lot : prix du lot + « Lot indivisible : X/N » | Épic 3 | ✓ Couvert |
| FR-046 | Scan article de lot : nom du lot en rouge + compteur X/N | Épic 4 | ✓ Couvert |
| FR-047 | Validation bloquée jusqu'à ce que le lot soit complet | Épic 4 | ✓ Couvert |
| FR-048 | Lot complet vendu au prix global | Épic 4 | ✓ Couvert |
| FR-049 | Bilan de vente imprimable par vendeur en phase Post-vente | Épic 5 | ✓ Couvert |
| FR-050 | Bilan : articles vendus, invendus + table, total brut, commission, reversement | Épic 5 | ✓ Couvert |
| FR-051 | Le bénévole solde le vendeur : saisit le montant espèces, clique Solder | Épic 5 | ✓ Couvert |
| FR-052 | Bouton « Non réclamé » : reversement → recettes association | Épic 5 | ✓ Couvert |
| FR-053 | Vendeurs non soldés identifiables avec numéro de téléphone | Épic 5 | ✓ Couvert |
| FR-054 | Bilan journalier générable par l'admin en phase Vente | Épic 5 | ✓ Couvert |
| FR-055 | Bilan d'édition généré à la clôture | Épic 5 | ✓ Couvert |
| FR-056 | Rapport des vendeurs non soldés (avec numéro de téléphone) | Épic 5 | ✓ Couvert |
| FR-057 | Tous les rapports générés en PDF | Épic 5 | ✓ Couvert |
| FR-058 | Rapports accessibles à l'admin uniquement | Épic 5 | ✓ Couvert |
| FR-059 | Éditions archivées : métriques agrégées + détail via PDF uniquement | Épic 5 | ✓ Couvert |
| FR-060 | L'admin crée/modifie/désactive comptes bénévoles, réinitialise mots de passe | Épic 1 | ✓ Couvert |
| FR-061 | Un seul compte admin par instance | Épic 1 | ✓ Couvert |
| FR-062 | Premier lancement : Admin/Admin, changement de mot de passe forcé | Épic 1 | ✓ Couvert |
| FR-063 | Réinitialisation mot de passe admin via commande CLI serveur | Épic 1 | ✓ Couvert |
| FR-064 | Rôles Admin/Bénévole strictement séparés | Épic 1 | ✓ Couvert |
| FR-065 | Interface bénévole adaptée à la phase active | Épic 1 | ✓ Couvert |
| FR-066 | Les sessions n'expirent pas automatiquement | Épic 1 | ✓ Couvert |
| FR-067 | Chaque compte mémorise une préférence de langue (EN/FR) | Épic 1 | ✓ Couvert |
| FR-068 | Serveur fonctionnel sur Linux, macOS, Windows | Épic 1 | ✓ Couvert |
| FR-069 | Configuration minimale : Raspberry Pi 4 (2 Go RAM) | Épic 1 | ✓ Couvert |
| FR-070 | Déployé via Docker Compose, données dans volumes persistants | Épic 1 | ✓ Couvert |
| FR-071 | Mises à jour via `docker compose pull && docker compose up -d` | Épic 1 | ✓ Couvert |
| FR-072 | Postes clients accèdent via navigateur, aucune installation locale | Épic 1 | ✓ Couvert |
| FR-073 | Page paramètres admin : nom association, commission, langue, largeur ticket | Épic 1 | ✓ Couvert |
| FR-074 | Guide d'installation exhaustif pour non-techniciens | Épic 1 | ✓ Couvert |
| FR-075 | Toute l'impression acheminée via le serveur central | Épic 3 | ✓ Couvert |
| FR-076 | Imprimante thermique USB + file séquentielle | Épic 3 | ✓ Couvert |
| FR-077 | Imprimante A4 USB : PDF envoyé directement sans aperçu | Épic 3 | ✓ Couvert |
| FR-078 | Déclenchement impression côté serveur, aucune action client | Épic 3 | ✓ Couvert |
| FR-079 | Erreur d'impression : notification explicite dans l'interface | Épic 3 | ✓ Couvert |
| FR-080 | Nouvelle édition peut copier catégories/tables depuis une existante | Épic 2 | ✓ Couvert |
| FR-081 | Le caissier peut retirer un lot entier du panier | Épic 4 | ✓ Couvert |
| FR-082 | L'admin peut revenir en arrière d'une phase, données préservées | Épic 2 | ✓ Couvert |
| FR-083 | Catalogue filtrable/triable accessible durant toutes les phases | Épic 6 | ✓ Couvert |
| FR-084 | Filtres du catalogue : nom, code-barres, catégorie, table, statut, vendeur | Épic 6 | ✓ Couvert |
| FR-085 | Catalogue triable par n'importe quelle colonne | Épic 6 | ✓ Couvert |
| FR-086 | Catalogue : édition active uniquement, indisponible après Nettoyage | Épic 6 | ✓ Couvert |
| FR-087 | En phase Vente : ajout article depuis catalogue au panier (repli scanner) | Épic 6 | ✓ Couvert |
| FR-088 | « Nettoyage de l'édition » : suppression définitive articles, rollback désactivé | Épic 2 | ✓ Couvert |
| FR-089 | Commission appliquée normalement aux articles incomplets vendus | Épic 5 | ✓ Couvert |
| FR-090 | Transition de phase avec panier actif : panier annulé, message bénévole | Épic 4 | ✓ Couvert |

### Exigences Manquantes

Aucune. Tous les FRs du PRD sont couverts dans les épics.

### Statistiques de Couverture

- **Total FRs PRD :** 90
- **FRs couverts dans les épics :** 90
- **Pourcentage de couverture :** **100 %**

### Répartition par Épic

| Épic | FRs Couverts | Groupes PRD |
|------|-------------|-------------|
| Épic 1 — Fondation, Auth & i18n | FR-001–007, FR-060–067, FR-068–074 (22 FRs) | F1, F7, F8 |
| Épic 2 — Gestion du cycle de vie des éditions | FR-008–016, FR-080, FR-082, FR-088 (12 FRs) | F2 |
| Épic 3 — Enregistrement vendeurs & Dépôt | FR-017–032, FR-043–045, FR-075–079 (24 FRs) | F3, F9 |
| Épic 4 — Point de vente | FR-033–042, FR-046–048, FR-081, FR-090 (15 FRs) | F4 |
| Épic 5 — Post-vente, Reversements & Rapports | FR-049–059, FR-089 (12 FRs) | F5, F6 |
| Épic 6 — Catalogue articles | FR-083–087 (5 FRs) | F10 |

### Observations

1. **Regroupement logique** : L'Epic 1 regroupe F1 (i18n), F7 (comptes) et F8 (déploiement) car ce sont des éléments de fondation transverses. Ce regroupement est cohérent — ils doivent tous être en place avant les fonctionnalités métier.

2. **Epic 3 absorbe F9** : L'infrastructure d'impression (F9 : FR-075–079) est intégrée dans Epic 3 plutôt que dans un épic séparé. Justification valide : l'impression est déclenchée lors du dépôt ; regrouper les deux évite une dépendance inter-épics.

3. **Epic 5 couvre F5 + F6** : Post-vente et Rapports sont naturellement liés (même phase, même acteurs). Regroupement pertinent.

4. **FR-089 placé dans Epic 5** : FR-089 (commission sur articles incomplets) est logiquement lié aux calculs de reversement de l'Épic 5. Correctement placé.

5. **FR-090 placé dans Epic 4** : FR-090 (annulation panier lors d'une transition de phase) est logiquement lié au POS (Épic 4), bien qu'il apparaisse dans F10 dans le PRD. L'épic a raison de le placer en Epic 4.

6. **Erratum Step 2** : La note initiale indiquant que « FR-083 n'existe pas dans la numérotation finale » était incorrecte. FR-083 existe dans F10 et est correctement couvert dans l'Épic 6.

---

## Évaluation de l'Alignement UX

### Statut de la Documentation UX

**Trouvée** — `ux-PluriBourse-2026-06-09/DESIGN.md` (tokens visuels, composants, typographie) + `EXPERIENCE.md` (comportements, flux, accessibilité) — statut `final`, accompagnés de 5 maquettes HTML.

### Alignement UX ↔ PRD

**Couverture complète :** Les 22 exigences UX-DR (UX-DR1 à UX-DR22) sont toutes tracées dans les épics. EXPERIENCE.md référence explicitement le PRD et l'architecture comme sources. Les FRs sont cités par numéro dans EXPERIENCE.md, attestant d'une revue PRD rigoureuse. Les contraintes RGPD (pas de nom vendeur sur les étiquettes, FR-027) sont correctement intégrées dans les spécifications des composants.

**Divergence identifiée — Rapport vendeurs non soldés :**

| Document | Comportement spécifié |
|---|---|
| PRD FR-057 | "Tous les rapports sont générés en PDF" |
| EXPERIENCE.md + Story 5.5 | La liste vendeurs non soldés "ouvre la vue d'impression du navigateur" |

Légère divergence : le PRD attendrait un PDF OpenPDF, mais l'UX et les épics optent pour la vue d'impression navigateur. Les épics reflètent le choix UX. Acceptable pour la v1, mais le PRD devrait être mis à jour pour cohérence documentaire.

**Fonctionnalité hors PRD — Exports CSV :**

EXPERIENCE.md et Story 5.5 introduisent des boutons « Exporter le catalogue » et « Exporter les reversements » (CSV, téléchargement direct). Aucun FR du PRD ne couvre cette fonctionnalité. Elle a été ajoutée lors de la phase UX sans mise à jour du PRD. L'implémentation est prévue, mais sans traçabilité FR formelle.

### Alignement UX ↔ Architecture

Toutes les exigences UX sont supportées architecturalement :

| Exigence UX | Support architectural | Statut |
|---|---|---|
| Phase chip temps réel (UX-DR4, fade 150ms) | SSE `phase-changed` via `SseEmitterRegistry` | ✓ |
| Annulation panier SSE (UX-DR21) | Événement `basket-cancelled` défini dans ARCH-012 | ✓ |
| Session sans expiration (FR-066) | Spring Session JDBC, `server.servlet.session.timeout=-1` | ✓ |
| Desktop-only (pas de breakpoints < 1024px) | Non contredit par l'architecture | ✓ |
| Scanner AZERTY/QWERTY, refocus 500ms (UX-DR10) | Key code mapping dans `scanner.component.ts` | ✓ |
| Angular Material 3 + DM Sans (UX-DR1, UX-DR2) | `@angular/material` (MIT) + Google Fonts | ✓ |
| Erreurs métier → messages UX naturels | `@ControllerAdvice` + RFC 7807, mappés côté Angular | ✓ |
| Formulaires réactifs + validation blur (UX) | Angular reactive forms + Bean Validation sur DTOs | ✓ |

### Avertissements

Aucun écart architectural. La divergence "browser print vs PDF" sur le rapport vendeurs non soldés est une décision UX délibérée, non un écart architectural.

---

## Revue Qualité des Épics

### Validation Valeur Utilisateur

| Épic | Orienté utilisateur ? | Indépendance | Résultat |
|---|---|---|---|
| Épic 1 — Fondation, Auth & i18n | ✓ Admins + bénévoles se connectent, configurent, travaillent dans leur langue | Standalone | ✓ |
| Épic 2 — Cycle de vie des éditions | ✓ Admin pilote les phases | Requiert Épic 1 uniquement | ✓ |
| Épic 3 — Enregistrement vendeurs & Dépôt | ✓ Bénévoles enregistrent et impriment | Requiert Épic 1 + 2 | ✓ |
| Épic 4 — Point de vente | ✓ Bénévoles vendent, gèrent lots, sécurité multi-postes | Requiert Épic 1 + 2 + 3 | ✓ |
| Épic 5 — Post-vente & Rapports | ✓ Reversements, rapports, clôture | Requiert Épic 1–4 | ✓ |
| Épic 6 — Catalogue articles | ✓ Recherche/filtrage toutes phases | Requiert Épic 1–3 (+ Épic 4 pour Story 6.2) | ⚠️ voir ci-dessous |

### Violations par Sévérité

#### ✅ Résolue — Dépendance implicite Épic 6 → Épic 4 (Story 6.2)

Dépendance documentée dans la description d'Épic 6 : "Story 6.2 requiert les endpoints basket d'Épic 4 — à implémenter après Épic 4."

#### ✅ Résolue — Exports CSV (Story 5.5) sans traçabilité FR dans le PRD

FR-091 et FR-092 ajoutés dans l'addendum PRD. Story 5.5 dispose désormais d'une traçabilité complète.

#### ⚠️ Mineure — Story 1.1 : perspective développeur

Story 1.1 est formulée "En tant que développeur" — non orientée utilisateur final. Acceptable pour une story de setup initial (conforme ARCH-001), mais borderline selon les standards BMad.

**Remédiation (optionnelle) :** Reformuler en "En tant qu'équipe de déploiement" ou laisser tel quel — non bloquant.

#### ✅ Résolue — Rapport vendeurs non soldés

FR-056 est couvert par la page de solde existante (`/volunteer/settlement`). Aucun PDF ni vue d'impression dédiés. L'addendum PRD documente l'exception à FR-057. EXPERIENCE.md et Story 5.5 mis à jour en conséquence.

### Revue des Critères d'Acceptation

Sondage sur 6 stories représentatives :

| Story | Format BDD | Testable | Cas d'erreur | Résultat |
|---|---|---|---|---|
| Story 1.2 (Auth) | ✓ | ✓ | ✓ 403, session | ✓ |
| Story 2.2 (Transitions) | ✓ | ✓ | ✓ rollback désactivé | ✓ |
| Story 3.1 (Vendeurs) | ✓ | ✓ | ✓ RGPD | ✓ |
| Story 4.4 (Concurrence) | ✓ | ✓ | ✓ 409, Testcontainers | ✓ |
| Story 5.1 (Reversements) | ✓ | ✓ | ✓ Non réclamé | ✓ |
| Story 6.2 (Catalogue→panier) | ✓ | ✓ | ✓ déjà vendu, déjà dans panier | ✓ |

**Qualité des ACs : élevée.** Tous les cas d'erreur métier significatifs sont couverts. Format BDD respecté systématiquement. Spécificité suffisante pour démarrer l'implémentation.

---

## Synthèse et Recommandations

### Statut Global de Préparation

## ✅ PRÊT POUR L'IMPLÉMENTATION

Le projet PluriBourse dispose d'une base solide : PRD complet (90 FRs), architecture mature ("PRÊT POUR L'IMPLÉMENTATION" selon le document d'architecture lui-même), UX finalisée avec maquettes, épics de qualité élevée avec ACs BDD testables et couverture FR à 100 %.

### Problèmes Critiques Nécessitant une Action Immédiate

Aucun problème bloquant l'implémentation n'a été identifié.

### Problèmes Importants à Traiter Avant le Sprint Planning

1. **Exports CSV sans FR (Story 5.5)** — Ajouter FR-091/FR-092 dans un addendum PRD, ou documenter explicitement ces exports comme extensions UX acceptées hors scope PRD. Action requise avant que Story 5.5 entre en sprint pour éviter une implémentation sans traçabilité.

2. **Dépendance Épic 6 → Épic 4 (Story 6.2)** — Annoter Épic 6 pour signaler que Story 6.2 requiert Epic 4 complété. Évite un risque de séquençage incorrect lors du sprint planning.

### Recommandations pour les Étapes Suivantes

1. Traiter les deux points importants ci-dessus (15-20 minutes de mise à jour documentaire)
2. Lancer `/bmad-sprint-planning` dans une nouvelle fenêtre de contexte pour produire le plan de sprint
3. Séquencer les épics dans l'ordre 1 → 2 → 3 → 4 → 5 → 6 — respecte toutes les dépendances
4. Traiter Story 6.2 uniquement après que les endpoints basket d'Epic 4 sont livrés

### Note Finale

Cette évaluation a identifié **4 observations** (0 critique, 2 majeures, 2 mineures). Les observations majeures sont des lacunes documentaires, pas des défauts de conception. Le projet peut démarrer l'implémentation dès maintenant ; les deux corrections documentaires peuvent être effectuées en parallèle du sprint planning.

**Date d'évaluation :** 2026-06-10
**Évaluateur :** BMad Check Implementation Readiness (étapes 4-6 reprises suite à interruption)
