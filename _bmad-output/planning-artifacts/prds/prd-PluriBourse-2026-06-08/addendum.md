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

**FR-056 — Liste des vendeurs non soldés**

FR-056 est couvert par la page de solde `/volunteer/settlement` (et la fiche vendeur admin), qui affiche déjà les vendeurs non soldés avec leur numéro de téléphone visible (FR-053). Aucun rapport PDF ni vue d'impression dédiés ne sont nécessaires pour ce cas d'usage — une page affichant les données en direct est plus simple et plus utile.

Exception à FR-057 : FR-056 n'est **pas** implémenté sous forme de PDF généré côté serveur. C'est la seule exception à la règle "tous les rapports sont en PDF".

---

**Exigences fonctionnelles complémentaires — Exports CSV**

Issues de la phase UX, non présentes dans le PRD initial :

- **FR-091 :** En phase Post-vente et Clôturée, l'administrateur peut exporter le catalogue articles au format CSV (articles avec leur statut vendu/invendu). Le téléchargement est déclenché directement sans boîte de dialogue.
- **FR-092 :** En phase Post-vente et Clôturée, l'administrateur peut exporter les reversements au format CSV. Le téléchargement est déclenché directement sans boîte de dialogue.

Ces deux exports sont couverts par Story 5.5 (Épic 5).

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
[étiquette article 1 : nom édition / ligne vide / --- catégorie --- / nom+prix / /!\ INCOMPLET si applicable / Table n°X / ligne vide / graphique Code 128 / numéro de code-barres / ligne vide]
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
