---
created: 2026-06-11
updated: 2026-06-11
---

# Questions ouvertes — PluriBourse

Décisions de design non encore tranchées définitivement. À relire avant d'implémenter la feature concernée.

**Statuts :**
- ⚠️ À valider — décision par défaut retenue dans le PRD, mais à confirmer avant implémentation
- ✅ Tranchée — décision prise, peut être archivée
- 🔄 En discussion

| ID | FR | Feature | Question | Décision par défaut (PRD/Epics) | Statut |
|---|---|---|---|---|---|
| OQ-001 | FR-047 | Lots en caisse | Bloquer la validation du paiement si le lot n'est pas complet, ou permettre la vente d'un lot partiel avec un simple avertissement ? | **Avertissement non bloquant** — la vente d'un lot incomplet est autorisée ; une notification inline avertissement s'affiche dans le panier mais le bouton « Valider » reste actif | ✅ Tranchée |
| OQ-002 | FR-053 | Post-Vente — vendeurs non soldés | Les bénévoles voient-ils le téléphone et l'email des vendeurs non soldés dans l'écran de solde ? Ces données sont personnelles (RGPD). Si non, quelle information leur est utile pour gérer le guichet ? | **Non** — les bénévoles ne voient pas téléphone ni email (RGPD). Ces colonnes sont réservées à la vue admin (`/admin/settlement`). La vue bénévole affiche nom, prénom, montant dû et statut. FR-095 et les critères d'acceptation sont déjà conformes. | ✅ Tranchée |
