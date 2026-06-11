# Addendum — PluriBourse PRD

Créé : 2026-06-08

---

## Infrastructure & déploiement

**Décision retenue :** Docker Compose + MariaDB

- Distribution via un `docker-compose.yml` unique embarquant l'application Spring Boot et MariaDB
- Données stockées dans des volumes Docker persistants
- Commande de démarrage : `docker compose up -d`
- Commande de mise à jour : `docker compose pull && docker compose up -d`
- Docker Desktop (Windows/macOS) ou Docker Engine (Linux/RPi4) côté utilisateur

**Contraintes documentation :** Le guide d'installation doit couvrir exhaustivement :
1. Installation Docker Desktop (Windows, macOS) / Docker Engine (Linux, RPi4)
2. Téléchargement et lancement du docker-compose.yml
3. Premier lancement et changement du mot de passe admin
4. Configuration initiale (nom association, taux commission, langue documents, largeur ticket)
5. Procédure de reset mot de passe admin (commande terminal)
6. Procédure de mise à jour

**Sauvegarde :** hors scope v1. En v2, envisager export du volume MariaDB ou dump SQL schedulé.

---

## Décisions de conception post-UX

**FR-095 — Page de solde (anciennement FR-056)**

FR-056 a été supprimé et remplacé par FR-095. La liste des vendeurs non soldés n'est pas un rapport — c'est le point d'entrée de F5. La page de solde affiche tous les vendeurs de l'édition, avec un filtre par statut (soldé / non soldé) et des actions par ligne (imprimer bilan, solder, non réclamé).

La page est accessible via deux routes distinctes partageant le même composant Angular : `/volunteer/settlement` (bénévoles) et `/admin/settlement` (admin). L'admin voit en plus les colonnes téléphone et email, affichées conditionnellement selon le rôle.

FR-057 s'applique sans exception : tous les rapports sont en PDF. La page de solde n'est pas un rapport.

---

**Exports CSV (FR-091, FR-092)**

Issues de la phase UX, intégrées dans le corps du PRD (§F6). Couvertes par Story 5.5 (Épic 5).

---

**Exigences fonctionnelles complémentaires — Moyen de paiement**

Issues de précisions de spécification post-UX :

- **FR-094 :** Le bilan journalier (FR-054) et le bilan d'édition (FR-055) incluent une ventilation des recettes par moyen de paiement (total espèces, total chèques, total carte). Couvert par Stories 5.3 et 5.4 (Épic 5).

*Note : FR-093 (sélection obligatoire du moyen de paiement) a été intégré dans le corps du PRD (§F4).*

---

## Architecture i18n (EN/FR)

**Contexte :** PluriBourse supporte deux langues (EN/FR). Les traductions front et back sont de nature différente — aucun partage de fichiers n'est nécessaire.

**Décision retenue : Option A — fichiers séparés**

- **Frontend (Angular) :** ngx-translate avec fichiers JSON (`en.json`, `fr.json`). Runtime language switching sans double build. Standard Angular pour les apps multilingues.
- **Backend (Spring Boot) :** Spring `MessageSource` avec fichiers `.properties` (`messages_en.properties`, `messages_fr.properties`). Utilisé pour la génération des documents imprimés (bon de dépôt, facture acheteur, document de reversement, rapports).

**Convention recommandée :** adopter une convention de nommage de clés cohérente entre front et back pour les termes métier partagés (ex. `seller.label`, `edition.label`) afin de limiter la dérive à long terme.

## Impression étiquettes — imprimante thermique 57mm

**Contexte :** Labels articles imprimés sur rouleau autocollant 57mm via imprimante thermique USB connectée au serveur.

**Format du rouleau :**
```
[séparateur vendeur : nom vendeur + édition]
[étiquette article 1 : nom édition / ligne vide / --- catégorie --- / nom+prix / /!\ INCOMPLET si applicable / Table n°X / ligne vide / graphique Code 128 / numéro de code-barres au format XXXX-XXXX / ligne vide]
[séparateur article]
[étiquette article 2]
...
[séparateur article]
[étiquette article N]
```

**Contraintes techniques :**

- Largeur utilisable : ~50-53mm sur rouleau 57mm
- Hauteur étiquette : variable (rouleau continu, ~50-60mm par étiquette recommandé)
- Protocole : ESC/POS (standard pour imprimantes thermiques 57/58mm)
- Bibliothèque Java candidate : `escpos-coffee` ou équivalent
- Code 128 généré server-side, rendu en bitmap avant envoi ESC/POS
- File d'impression côté serveur : jobs séquentiels, déclenchés à la validation du dépôt

**Options écartées :**

- Option B (backend sert les traductions via API) — couplage front↔back inutile pour un outil communautaire.
- Option C (fichiers JSON partagés lus des deux côtés) — adaptateur Spring custom à maintenir sans gain réel, étant donné que les traductions front et back sont de nature différente.
