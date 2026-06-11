# Revue de Couverture PRD — UX PluriBourse
Date : 2026-06-12
Relecteur : Lens Couverture PRD (révision post-mise à jour EXPERIENCE.md)

---

## Résumé

La mise à jour du 2026-06-12 de EXPERIENCE.md résout intégralement les 2 ÉCHECS et les 9 PRÉOCCUPATIONS de la revue précédente (2026-06-09). La spine couvre maintenant tous les flux opérationnels principaux avec une fidélité suffisante pour guider l'implémentation. Trois lacunes nouvelles subsistent : la page de file d'impression admin (route présente dans l'IA, zéro contenu spécifié), le comportement de blocage de la clôture d'édition lorsque des vendeurs restent non soldés (FR-096), et le message d'avertissement pour un article incomplet scanné en caisse (FR-037). Aucune ne constitue un échec bloquant, mais la première et la deuxième nécessitent une spec avant développement.

---

## Résolu

### RÉSOLU — Vue en lecture seule de l'édition archivée et état post-Nettoyage (FR-059, FR-086, FR-088)

**Précédent statut : ÉCHEC**

La spine contient désormais :
- Composant **"Édition archivée — vue détail"** — bannière lecture seule, métriques agrégées (6 champs), sous-section vendeurs en lecture seule, accès conditionnel au catalogue, bouton "Nettoyer l'édition".
- Composant **"Catalogue — état post-Nettoyage"** — message centré, absence de filtres et d'actions.
- Composant **"Action 'Nettoyer l'édition'"** — dialog de confirmation avec libellé d'irréversibilité, post-état catalogue vide.
- State Pattern **"Phase Clôturée — édition archivée"** et **"Catalogue post-Nettoyage"** dans la table State Patterns.
- State Pattern **"Retour arrière désactivé après Nettoyage"** avec message inline icône `lock`.

Couverture complète de FR-059, FR-086, FR-088.

---

### RÉSOLU — Déclencheur d'impression du bilan de vente en post-vente (FR-049, FR-050, FR-065)

**Précédent statut : ÉCHEC**

La spine contient désormais :
- Composant **"Récapitulatif reversement imprimable"** — bouton "Imprimer le récapitulatif" sur la ligne vendeur de `/volunteer/settlement` et depuis la fiche vendeur admin, avec feedback spinner et toast.
- **Flow 5** (nouveau) — parcours narratif complet : impression du bilan avant règlement, confirmation d'impression, puis solde du vendeur.

Couverture complète de FR-049, FR-050, FR-065.

---

### RÉSOLU — Flux de retour en arrière de phase (FR-082)

**Précédent statut : PRÉOCCUPATION**

Composant **"Contrôle de phase — retour arrière"** spécifié avec : bouton secondaire "Revenir à la phase précédente", dialog de confirmation avec libellé de préservation des données, et cas particulier Clôturé → Post-vente après Nettoyage (bouton absent, message inline icône `lock`). State Pattern correspondant présent.

---

### RÉSOLU — Anonymisation RGPD des vendeurs (FR-021)

**Précédent statut : PRÉOCCUPATION**

Composant **"Fiche vendeur admin — suppression RGPD"** spécifié : bouton "Supprimer ce vendeur" (style secondary/error, phase Dépôt uniquement), dialog de confirmation avec description des conséquences et caractère irréversible, post-suppression avec redirection et toast.

---

### RÉSOLU — Déclencheur d'impression du bordereau de dépôt (FR-031)

**Précédent statut : PRÉOCCUPATION**

La section Interaction Primitives — Impression spécifie explicitement : à la validation du dépôt (FR-028, FR-031), l'impression des étiquettes et du bordereau est déclenchée automatiquement sans bouton supplémentaire. Le bordereau est également rejouable depuis la fiche vendeur via "Réimprimer le bordereau".

---

### RÉSOLU — Déclencheur d'impression de la facture acheteur (FR-040, FR-041)

**Précédent statut : PRÉOCCUPATION**

Le composant **"Panier POS"** spécifie l'étape de validation du paiement et l'**état post-validation** : bouton "Imprimer la facture" visible 30 secondes, disparaît automatiquement, scanner reprend le focus. State Pattern **"Post-validation POS — facture disponible"** confirmé dans la table des états.

---

### RÉSOLU — Contenu de la page Paramètres admin (FR-073, FR-032, FR-005)

**Précédent statut : PRÉOCCUPATION**

Composant **"Page Paramètres instance"** spécifié : 4 champs (nom association, taux de commission par défaut, langue des documents par défaut, largeur du ticket thermique), notes explicatives sur la portée "nouvelles éditions uniquement", feedback spinner + toast.

---

### RÉSOLU — UX du gel du taux de commission (FR-016)

**Précédent statut : PRÉOCCUPATION**

Composant **"Fiche édition — taux de commission"** spécifié : champ modifiable en phase Préparation uniquement, état désactivé visuel (fond surface-variant, texte on-surface-variant) avec message inline "Taux gelé pour cette édition" dès la phase Dépôt.

---

### RÉSOLU — Page compte utilisateur (FR-003, FR-067)

**Précédent statut : PRÉOCCUPATION**

Composant **"Page compte utilisateur"** spécifié : sélecteur EN/FR avec application immédiate sans rechargement (ngx-translate runtime switch), formulaire changement de mot de passe (3 champs), validation nouveau ≠ actuel.

---

### RÉSOLU — Changement de mot de passe forcé au premier lancement (FR-062)

**Précédent statut : PRÉOCCUPATION**

Composant **"Premier lancement — changement de mot de passe forcé"** spécifié : redirection vers `/account/force-password`, sidebar masquée, message d'invite, aucun contournement possible. **Flow 6** (nouveau) — parcours narratif complet depuis `docker compose up -d` jusqu'à la configuration initiale.

---

### RÉSOLU — Mécanisme de livraison des conflits de scan concurrent (NFR-002, FR-036)

**Précédent statut : PRÉOCCUPATION**

La section **Interaction Primitives — Conflit de scan concurrent** spécifie explicitement : réponse HTTP 409 synchrone au scan (pas SSE), verrou optimiste côté serveur, notification inline rouge, scanner reste actif. State Pattern **"Conflit de scan concurrent"** confirmé dans la table des états.

---

### RÉSOLU — Exports CSV (FR-091, FR-092)

FR-091 et FR-092 sont couverts dans le composant **"Page Rapports"** : boutons "Exporter le catalogue" et "Exporter les reversements" disponibles en phases Post-vente et Clôturée, téléchargement direct sans dialog, conformément au PRD.

---

### RÉSOLU — Moyens de paiement POS (FR-093, FR-094)

FR-093 est couvert dans le composant **"Panier POS"** : trois boutons radio (Espèces / Chèque / Carte), sélection obligatoire, bouton "Confirmer" désactivé tant qu'aucun moyen de paiement n'est sélectionné, calcul de monnaie si espèces. FR-094 est couvert dans le composant **"Page Rapports"** : ventilation par moyen de paiement dans le rapport de caisse journalier et le rapport de synthèse.

---

## Constats actifs

### PASS — Couverture du cycle de vie des phases

Les cinq phases (Préparation → Dépôt → Vente → Post-vente → Clôturé) sont représentées. L'IA mappe chaque surface bénévole à la phase correcte. La mise à jour via SSE (`phase-changed`) est spécifiée. Les Flows 4, 5 et 6 couvrent respectivement : transition avant, flux post-vente, et premier lancement. Le retour en arrière et l'état post-Nettoyage sont couverts (voir RÉSOLU).

### PASS — Flux POS principaux (FR-033 à FR-048, FR-081, FR-090, FR-093)

Scanner, AZERTY/QWERTY, panier, lots avec compteur X/N, validation bloquée sur lot incomplet, retrait de lot (FR-081), annulation lors d'un changement de phase (FR-090), sélection obligatoire du moyen de paiement (FR-093) et calcul de monnaie : tous spécifiés. Couverture solide.

### PASS — Post-vente et reversements (FR-049 à FR-053, FR-065, FR-095)

Flow 5 couvre l'impression du bilan avant règlement. Composant "Page Reversements" spécifié avec colonnes conditionnelles par rôle. Composant "Formulaire de solde vendeur" spécifié (montant supérieur bloquant, montant inférieur avec confirmation). Cas "Non réclamé" couvert par Flow 3. Couverture complète.

### PASS — Exports et rapports (FR-054, FR-055, FR-057, FR-091, FR-092, FR-094)

Page Rapports spécifiée avec contenu conditionnel selon la phase, ventilation par moyen de paiement, exports CSV directs. Couverture suffisante.

### PASS — Premier lancement et administration (FR-062, FR-073, FR-016)

Flow 6 + composant "Premier lancement" + composant "Page Paramètres instance" + composant "Fiche édition — taux de commission" forment un ensemble cohérent couvrant le parcours d'installation et de configuration initiale.

### PASS — Plancher d'accessibilité et ton (NFR-004, NFR-005)

WCAG 2.2 AA, piège focus, aria-live scanner, aria-label phase chip, cibles 44×44px : tous spécifiés. Section Voix et Ton approfondie. Inchangé depuis la revue précédente.

---

### PRÉOCCUPATION — Page de file d'impression admin (FR-079)

FR-079 spécifie que l'admin dispose d'une vue de l'état de la file d'impression et des erreurs en cours pour diagnostiquer le problème, et peut relancer un job en erreur ou l'ignorer pour reprendre la file. La route `/admin/print-queue` est présente dans l'IA ("File d'impression — Toutes phases") mais aucun composant ni spec de contenu ne décrit cette page : quelles colonnes affiche-t-elle, comment l'admin interagit avec les jobs en erreur (bouton "Relancer" ? "Ignorer" ?), quel état visuel différencie un job en attente, en cours, en erreur ou traité. La note toast-imprimante-hors-ligne dans les State Patterns décrit la notification utilisateur mais pas la gestion admin de la file. Ce composant nécessite une spec avant développement.

**Recommandation :** Ajouter un composant "Page file d'impression" spécifiant les colonnes (date, job, vendeur, statut), les actions par ligne (Relancer, Ignorer), et l'état vide "Aucun job en attente."

---

### PRÉOCCUPATION — Bouton de clôture conditionnel et notification vendeurs non soldés (FR-096)

FR-096 spécifie que le bouton "Clôturer l'Édition" est désactivé tant qu'au moins un vendeur est dans un statut autre que Soldé ou Non réclamé, avec une notification inline affichant le nombre de vendeurs non soldés et un lien vers la page de solde. EXPERIENCE.md couvre la page de contrôle de phase (route dans l'IA, composant "Contrôle de phase — retour arrière") mais ne spécifie pas l'état désactivé du bouton de clôture, le libellé de la notification inline, ni le lien vers `/admin/settlement`. Cette interaction est sur le chemin critique de la clôture d'édition.

**Recommandation :** Étendre le composant "Contrôle de phase" (ou créer un état "Clôture conditionnelle") pour spécifier : bouton "Clôturer l'Édition" désactivé avec notification inline "X vendeur(s) non soldé(s) — Accéder à la page de solde" (lien cliquable). Préciser que le serveur renvoie 409 si la contrainte est violée côté API.

---

### FAIBLE — Avertissement article incomplet scanné en caisse (FR-037)

FR-037 exige que scanner un article incomplet affiche un avertissement informatif au caissier incluant le détail de ce qui manque. L'article peut tout de même être vendu. EXPERIENCE.md spécifie le comportement pour les articles déjà vendus (notification inline rouge) et pour les lots incomplets (notification inline orange), mais ne décrit pas le traitement UX d'un article individuel incomplet : aucun état dans State Patterns, aucune mention dans le composant "Panier POS". Ce cas est moins critique que les deux précédents (l'article est vendu sans blocage), mais le type de notification (inline ? toast ?) et son contenu (affichage du commentaire d'incomplétude ?) doivent être spécifiés.

**Recommandation :** Ajouter dans State Patterns un état "Article incomplet scanné — caisse" : notification inline orange sous le scanner, message incluant le champ commentaire de l'article (ex. "Cet article est incomplet : il manque le livret de règles."), sans blocage de la transaction.

---

## Recommandations

Ordonnées par priorité.

### 1. [MOYEN] Spécifier la page de file d'impression admin (FR-079)

Route `/admin/print-queue` présente dans l'IA sans composant ni spec de contenu. Ajouter un composant "Page file d'impression" avec colonnes, actions par ligne (Relancer, Ignorer), et états (en attente, en cours, en erreur, traité). Sans cette spec, la page sera implémentée de façon ad hoc.

### 2. [MOYEN] Spécifier le comportement de clôture conditionnelle (FR-096)

Ajouter un état ou une extension du composant "Contrôle de phase" pour le bouton "Clôturer l'Édition" désactivé avec notification inline. Ce comportement est sur le chemin critique de la clôture d'édition et doit être spécifié avant l'implémentation de F2.

### 3. [FAIBLE] Couvrir l'avertissement article incomplet au POS (FR-037)

Ajouter un State Pattern pour le scan d'un article incomplet en caisse : type de notification, contenu (inclusion du commentaire d'incomplétude), absence de blocage. Améliore la cohérence des spécifications des cas limites POS.
