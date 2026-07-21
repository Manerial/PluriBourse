---
baseline_commit: f0af4a45e7012fd3b279d6b5dffed0396252d67f
---

# Story 3.5: Génération & Impression des étiquettes thermiques

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole validant le dépôt d'un vendeur,
I want que les étiquettes d'articles soient automatiquement imprimées sur l'imprimante thermique,
so that les articles soient physiquement étiquetés immédiatement après le dépôt, sans étape manuelle.

## Acceptance Criteria

1. Chaque `Item` (individuel ou membre de lot) reçoit à sa création un code-barres Code 128 unique, généré côté serveur via ZXing, encodant 8 chiffres : 4 chiffres numéro du vendeur **dans l'édition** + 4 chiffres numéro de l'article **dans l'inventaire de ce vendeur** (FR-026).
2. Un nouvel endpoint « Valider le dépôt » (déclenché depuis `/volunteer/deposit`) est utilisable uniquement en phase Dépôt pour le vendeur sélectionné ; il met en file d'attente thermique un seul travail d'impression contenant, dans l'ordre : séparateur vendeur (nom + édition) → étiquette article → séparateur article → étiquette article → … pour **tous** les articles actuellement enregistrés de ce vendeur (FR-028, FR-030).
3. Le travail est routé vers l'imprimante thermique sélectionnée en session par le bénévole (`PrinterSelectionService.getSelectedPrinterId`, Story 3.9) ; si aucune imprimante thermique n'est sélectionnée en session, ou si elle est actuellement indisponible (détecté **avant** la mise en file, voir Dev Notes § Vérification de disponibilité), l'appel échoue avec une erreur 422 explicite — le frontend affiche un toast d'erreur persistant. Les articles déjà enregistrés ne sont pas affectés (aucune donnée à annuler, cette action ne fait que déclencher l'impression). Aucun retry automatique (cohérent avec FR-098 / Story 3.9).
4. Une étiquette d'article standard affiche, dans cet ordre : nom de l'édition — ligne vide — « --- Catégorie --- » — nom de l'article + prix — « /!\ INCOMPLET » sur une ligne dédiée si applicable — « Table n°X » — ligne vide — graphique Code 128 (image bitmap) — numéro de code-barres lisible au format `XXXX-XXXX` — ligne vide. **Aucun nom de vendeur n'apparaît** (RGPD, FR-027).
5. Une étiquette d'article de lot affiche « Prix du lot : X€ » à la place du prix individuel et « Lot indivisible : X/N » (X = position de l'article dans le lot par ordre croissant d'id, c'est-à-dire l'ordre de création ; N = nombre total d'articles du lot) (FR-045). Entre chaque étiquette d'article (individuel ou de lot), le rouleau insère un séparateur article (FR-030) — voir Dev Notes § Séparateurs pour son contenu exact.
6. La largeur ESC/POS appliquée (nombre de colonnes du raster/texte) correspond à la largeur configurée de l'imprimante thermique sélectionnée — 57 mm ou 80 mm (FR-032).
7. Tous les textes imprimés sur les étiquettes (« Catégorie », « INCOMPLET », « Table n° », « Prix du lot », « Lot indivisible ») sont résolus via `MessageSource` dans la langue documentaire de l'édition (`Edition.documentLanguage`), jamais codés en dur — couche distincte de l'i18n d'interface ngx-translate.

**Hors périmètre de cette story** (voir Dev Notes § Scope) : réimpression d'étiquettes depuis une fiche vendeur — aucune vue "fiche vendeur" n'existe encore ; elle sera introduite par la Story 3.6 (bordereau réimprimable), qui pourra alors étendre le même bouton aux étiquettes si confirmé en review. Le bordereau de dépôt PDF (Story 3.6) n'est pas déclenché par cette story mais partagera le même point d'entrée `DepositValidationService` (voir Dev Notes § Point d'extension 3.6).

## Tasks / Subtasks

- [x] Backend — numérotation vendeur & article (AC: 1)
  - [x] Migration Liquibase `017-item-and-seller-numbering.xml` : `addColumn` `seller_profiles.seller_number INT` + `items.item_number INT` (nullable d'abord), backfill par sous-requête corrélée `ROW_NUMBER() OVER (PARTITION BY ...)` (portable H2/MariaDB — la forme `UPDATE ... JOIN ... SET` a été essayée d'abord et rejetée par H2), puis `addNotNullConstraint`. **Écart par rapport au plan initial** : ajout de deux colonnes compteur supplémentaires — `editions.next_seller_number` et `seller_profiles.next_item_number` — voir Dev Notes § Concurrence pour la raison (MAX+1 s'est révélé bugué après suppression). Incluse dans `db.changelog-master.xml`.
  - [x] `EditionRepository` : `lockById` (verrou pessimiste), même pattern que `EditionCategoryRepository.lockById`
  - [x] `SellerRepository` : `lockById` (verrou pessimiste). *(`findMaxSellerNumberByEditionId` planifié initialement, supprimé — remplacé par `Edition.nextSellerNumber`, voir Dev Notes § Concurrence)*
  - [x] `ItemRepository` : `findAllBySellerProfileIdOrderByItemNumberAsc(Long sellerProfileId)` — **avec `JOIN FETCH edition`/`sellerProfile`/`lot`**, ajout non prévu au plan initial (voir Dev Notes § Chargement eager, bug de production réel découvert en test), et `findAllByLotIdOrderById(Long lotId)` (X/N d'un article de lot, AC5). *(`findMaxItemNumberBySellerProfileId` planifié initialement, supprimé — remplacé par `SellerProfile.nextItemNumber`)*
  - [x] `SellerService.create()` : verrouille l'édition puis assigne `sellerNumber = lockedEdition.getNextSellerNumber()` et incrémente le compteur, avant `save()`
  - [x] `ItemService.create()` et `LotService.create()` : verrouillent le vendeur (une seule fois avant la boucle pour `LotService`) puis assignent `itemNumber` depuis `SellerProfile.nextItemNumber`, incrémenté, avant `save()`
  - [x] `Item.getBarcode()`/`getFormattedBarcode()` : méthodes calculées (pas de colonne stockée)
- [x] Backend — dépendances impression (AC: 1, 4, 6)
  - [x] `pom.xml` : `com.google.zxing:core:3.5.4` + `com.google.zxing:javase:3.5.4` (encodage via `Code128Writer` directement, pas `MultiFormatWriter` — pas de gain à passer par l'abstraction multi-format alors que le format est fixé)
  - [x] Pas de `escpos-coffee` ajouté — commandes ESC/POS construites à la main en `byte[]` (`ThermalLabelRenderer`)
- [x] Backend — service de validation du dépôt & rendu des étiquettes (AC: 2, 3, 4, 5, 6, 7)
  - [x] Clés `print.label.category`/`incomplete`/`table`/`lotPrice`/`lotIndivisible` dans `messages.properties`/`messages_fr.properties`/`messages_en.properties`
  - [x] `ThermalLabelRenderer` (`org.pluribourse.print.service`) : construit le `byte[]` ESC/POS, encode le code-barres via ZXing `Code128Writer`, applique la largeur (57→384 dots, 80→576 dots), calcule X/N pour les articles de lot, insère le séparateur article entre étiquettes. **Bug corrigé pendant l'implémentation** : `MessageSource`/`MessageFormat` applique un formatage numérique sensible à la locale aux arguments `{0}` bruts (`Number`), ce qui corrompait silencieusement le prix du lot (`BigDecimal` "12.00" → reformaté) — tous les arguments numériques sont maintenant pré-formatés en `String` avant d'être passés à `getMessage()` (voir Dev Notes § Rendu ESC/POS)
  - [x] `ThermalPrintService.buildDepositJob(sellerProfile, items, locale)` : retourne un `PrintJob` (lambda), pas une classe qui "implémente" `PrintJob` sur le singleton Spring — nécessaire puisque chaque job porte des données par-appel (vendeur/articles/locale)
  - [x] `PrintQueueService.isAvailable(Long printerId)` ajoutée ; `PrinterSelectionService.isAvailable(Printer)` délègue désormais à cette méthode (déduplication)
  - [x] `DepositValidationService` (`org.pluribourse.item.service`) : `validateDeposit(Long sellerProfileId, HttpSession session)` — phase Dépôt, résolution vendeur, chargement des articles (avec associations eager), vérification synchrone de disponibilité imprimante (`InvalidPrinterSelectionException` réutilisée, 422), soumission du job
  - [x] Endpoint `POST /sellers/{id}/deposit/validate` sur `SellerController` → 204
- [x] Frontend — bouton de validation du dépôt (AC: 2, 3)
  - [x] `services/deposit.service.ts` : `validateDeposit(sellerProfileId): Observable<void>`
  - [x] `deposit-page.component.ts`/`.html` : bouton pleine largeur, désactivé si liste vide ou pendant soumission, toasts succès/erreur dédié/générique
  - [x] i18n : clés `volunteer.deposit.button.validate`, `success.validate`, `error.validate`, `error.printerUnavailable` dans `fr.json`/`en.json`
- [x] Tests backend : `ThermalLabelPrintingIT` (14 scénarios, E2E via contrôleurs + appels directs justifiés sur `ThermalLabelRenderer`/`ThermalPrintService`, même exception que `PrintInfrastructureIT` — aucun THERMAL ne peut passer sa vérification de connectivité dans cet environnement) — numérotation séquentielle vendeur/article, non-réutilisation après suppression, phase-gate, absence/indisponibilité d'imprimante (422), contenu des étiquettes (code-barres, absence du nom vendeur, lot), séparateur article, propagation d'erreur du `PrintJob`. Tous verts, suite complète sans régression.
- [x] Tests frontend : specs Vitest pour le bouton (désactivé si liste vide, toast succès/erreur dédié/générique) ; 369/369 tests verts, aucune régression

### Review Findings

- [x] [Review][Decision] Contenu du séparateur article non spécifié — `ThermalLabelRenderer.articleSeparator()` n'envoie qu'une commande de coupe partielle ESC/POS (`GS V 1`), sans texte. Ni `epics.md` ni la PRD ne précisent ce contenu ; auto-signalé par le développeur en Dev Notes § Séparateurs ("Signaler ce choix en review"). **Résolu 2026-07-20** : conservé tel quel — coupe sans texte acceptée définitivement, aucun changement de code requis.
- [x] [Review][Patch] Risque d'interblocage (deadlock) entre `ItemService.create()` et `LotService.create()` [pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java:47-52] — **Corrigé** : `ItemService.create()` verrouille désormais le vendeur avant `assignTable()`, même ordre que `LotService.create()`.
- [x] [Review][Patch] `lotPosition()` interroge la BDD à chaque rendu au lieu de réutiliser la liste d'articles déjà chargée [pluribourse-backend/src/main/java/org/pluribourse/print/service/ThermalLabelRenderer.java:90-94] — **Corrigé** : `renderLabel()` reçoit désormais la liste des articles du vendeur déjà chargée (`sellerItems`) et `lotPosition()` calcule X/N en mémoire, sans requête BDD ni dépendance à `ItemRepository`.
- [x] [Review][Patch] Javadoc obsolète sur `EditionRepository.lockById` [pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java:17-21] — **Corrigé** : Javadoc mis à jour pour décrire le compteur persisté `nextSellerNumber`.
- [x] [Review][Patch] Aucune garde serveur contre la validation d'un dépôt sans article [pluribourse-backend/src/main/java/org/pluribourse/item/service/DepositValidationService.java:40-44] — **Corrigé** : ajout d'un contrôle `items.isEmpty()` levant une nouvelle `EmptyDepositException` (422).
- [x] [Review][Patch] Toast de succès trompeur "Dépôt enregistré." [pluribourse-frontend/public/i18n/fr.json, en.json — clé `volunteer.deposit.success.validate`] — **Corrigé** : texte remplacé par "Étiquettes envoyées à l'impression." / "Labels sent to the printer."
- [x] [Review][Patch] Aucune protection contre le débordement du format 4+4 chiffres du code-barres [pluribourse-backend/src/main/java/org/pluribourse/item/entity/Item.java:62-68] — **Corrigé** : `getBarcode()`/`getFormattedBarcode()` lèvent désormais une `IllegalStateException` si `sellerNumber`/`itemNumber` dépasse 9999.
- [x] [Review][Patch] `lockById(...).orElseThrow()` sans exception dédiée (3 sites) [pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java:52, LotService.java:53, seller/service/SellerService.java (verrou édition)] — **Corrigé** : les 3 sites lèvent désormais `SellerNotFoundException`/`EditionNotFoundException`.
- [x] [Review][Patch] AC6 (largeur ESC/POS 80mm) non couverte par les tests [pluribourse-backend/src/test/java/org/pluribourse/print/ThermalLabelPrintingIT.java] — **Corrigé** : nouveau test `render_label_at_80mm_uses_a_wider_barcode_raster_than_57mm` (Order 15) comparant la taille du raster à 57mm et 80mm.
- [x] [Review][Defer] Duplication des clés i18n entre `messages.properties` et `messages_en.properties` [pluribourse-backend/src/main/resources/messages.properties] — déferré, préexistant : convention établie depuis la Story 1.6 (le fichier entier reproduit `messages_en.properties`), pas spécifique à cette story.
- [x] [Review][Defer] `deposit-page.component.ts` ne protège pas contre un changement de vendeur sélectionné pendant qu'une requête `validateDeposit()` est en vol [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts:115-133] — déferré, mineur : le toast final peut s'afficher après que le bénévole a changé de vendeur à l'écran ; cohérent avec le pattern déjà accepté ailleurs dans ce composant (aucune autre action async ne se protège contre ce cas).

#### Re-review 2026-07-20 (après application des 8 patches ci-dessus)

- [x] [Review][Patch] Aucune confirmation avant réimpression complète du dépôt — chaque clic sur « Valider le dépôt » réimprime l'intégralité des étiquettes du vendeur ; rien n'empêche un second clic après la fin de la requête (ou un second onglet) de déclencher une réimpression complète et de gaspiller un rouleau d'étiquettes physique. **Décision 2026-07-20** : ajouter une boîte de dialogue de confirmation (`ConfirmDialogService`, même pattern que les suppressions, Story 1.11) avant l'appel à `validateDeposit()` [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts:115-133] — **Corrigé** : boîte de confirmation ajoutée avant l'appel au service, clés i18n `volunteer.deposit.validateDialog.*`, test dédié pour le cas d'annulation.
- [x] [Review][Patch] Aucune borne sur les compteurs de numérotation vendeur/article (max 9999, format code-barres 4+4 chiffres) au moment de la création — l'échec (`IllegalStateException`) ne survient qu'au rendu de l'étiquette, sur le thread consommateur de la file d'impression (`PrinterQueueHandle.consume()`), et suspendrait la file entière pour tous les autres vendeurs de cette imprimante, sans erreur visible à la création [pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java:58-59, pluribourse-backend/src/main/java/org/pluribourse/item/service/LotService.java, pluribourse-backend/src/main/java/org/pluribourse/seller/service/SellerService.java:62-63] — **Corrigé** : garde explicite contre `Item.MAX_BARCODE_SEGMENT` (9999) à la création, nouvelles `TooManyItemsException`/`TooManySellersException` (422), avant tout verrouillage/incrémentation de compteur superflu.
- [x] [Review][Patch] `ThermalPrintService.buildDepositJob` — aucun timeout sur l'écriture du port série, contrairement à `SerialPrinterConnectivityChecker.openPort()` (Story 3.4) qui applique déjà `OPEN_TIMEOUT_MS` ; une imprimante bloquée/débranchée fige indéfiniment le thread consommateur de la file [pluribourse-backend/src/main/java/org/pluribourse/print/service/ThermalPrintService.java] — **Corrigé** : open/write/close bornés par un `CompletableFuture` avec timeout de 10s, même technique que `SerialPrinterConnectivityChecker.checkAccessibility`.
- [x] [Review][Patch] Migration `017-item-and-seller-numbering.xml` — les 4 changesets `<sql>` de backfill n'ont pas de bloc `<rollback>`, contrairement à la convention déjà en place dans le projet (`002-spring-session.xml`) [pluribourse-backend/src/main/resources/db/changelog/017-item-and-seller-numbering.xml] — **Corrigé** : bloc `<rollback>` ajouté (suppression des 4 colonnes).
- [x] [Review][Patch] Le patch de review précédente « garde serveur contre un dépôt vide » (`EmptyDepositException`) n'est couvert par aucun test — aucun scénario de `ThermalLabelPrintingIT` ne déclenche la branche `items.isEmpty()` [pluribourse-backend/src/test/java/org/pluribourse/print/ThermalLabelPrintingIT.java] — **Corrigé** : nouveau test `validate_deposit_for_seller_with_no_items_returns_422` (Order 16).
- [x] [Review][Patch] La ligne de prix d'un article individuel est codée en dur (`"%.2f€"`, directement en Java) au lieu de passer par `MessageSource` comme toutes les autres lignes imprimées de l'étiquette [pluribourse-backend/src/main/java/org/pluribourse/print/service/ThermalLabelRenderer.java:~92] — **Corrigé** : nouvelle clé `print.label.itemPrice={0} - {1}€` dans les 3 fichiers `messages*.properties`, ligne désormais résolue via `MessageSource`.
- [ ] [Review][Patch] La ligne de prix d'un article individuel est codée en dur (`"%.2f€"`, directement en Java) au lieu de passer par `MessageSource` comme toutes les autres lignes imprimées de l'étiquette [pluribourse-backend/src/main/java/org/pluribourse/print/service/ThermalLabelRenderer.java:~92]

## Dev Notes

### Scope — ce que cette story fait et ne fait pas

Cette story introduit **trois éléments qui n'existent pas encore dans le code**, bien que les ACs de l'épic (`epics.md` lignes 1190-1218) les présupposent implicitement :

1. **Numérotation séquentielle vendeur/article** — `SellerProfile`/`Item` n'ont aujourd'hui que leur `id` technique auto-incrémenté (global, pas par édition/vendeur). FR-026 exige explicitement un numéro **par édition** pour le vendeur et **par vendeur** pour l'article. Aucune story antérieure (3.1, 3.2, 3.3) n'a introduit ce champ — les Dev Notes de la Story 3.3 le confirment explicitement ("aucune génération de code-barres n'existe à ce stade, y compris pour les articles individuels créés en Story 3.2").
2. **L'action « Valider le dépôt »** — ni `ItemController` ni `SellerController` n'exposent aujourd'hui d'action de validation ; les articles sont créés au fil de l'eau. Le mockup (`mock-deposit.html` ligne 857-860) et le flow utilisateur (`EXPERIENCE.md` Flow 1, étape 6 "climax") confirment que c'est un bouton explicite en bas de la fiche vendeur — distinct de chaque sauvegarde d'article individuelle — qui déclenche l'impression groupée. C'est le point d'entrée que cette story doit créer.
3. **Toute la chaîne de rendu ESC/POS et de génération de code-barres** — aucune dépendance ZXing ni logique d'impression thermique n'existe (seul `jSerialComm`, le transport, existe depuis la Story 3.4).

**Ne pas confondre** « valider le dépôt » avec un état persisté : ce n'est **pas** un flag `depositValidated` sur `SellerProfile`. Chaque clic réimprime l'intégralité des étiquettes actuellement enregistrées pour ce vendeur (cohérent avec le texte de l'AC de l'épic : "toutes les étiquettes de ce vendeur"). Un double-clic accidentel réimprime tout — mitigé côté frontend par la désactivation du bouton pendant la requête, comme déjà pratiqué sur les autres formulaires du projet (`LotFormComponent`, Story 3.9).

**Décision de scope à confirmer avec l'utilisateur en review** (même logique que les scope calls de la Story 3.3) : le flow utilisateur (`EXPERIENCE.md` ligne 270) mentionne "les étiquettes sont rejouables depuis la fiche vendeur" en cas d'échec d'impression — mais aucune fiche vendeur (vue dédiée post-saisie) n'existe encore ; seule la page de saisie `/volunteer/deposit` existe. La Story 3.6 introduit explicitement cette vue pour réimprimer le bordereau PDF. Cette story ne construit **pas** de bouton de réimpression des étiquettes ; si l'utilisateur souhaite l'ajouter dès maintenant plutôt que d'attendre la Story 3.6, le signaler en review.

### Point d'extension Story 3.6

FR-031/l'épic (ligne 1229) précisent que le bordereau PDF est généré "en parallèle de l'impression des étiquettes — Story 3.5" au même moment de validation. Pour que la Story 3.6 puisse se greffer sans dupliquer la logique de résolution vendeur/phase/articles, structurer `DepositValidationService.validateDeposit()` comme le **point d'entrée unique** de la validation de dépôt (déjà nommé en ce sens dans les tâches ci-dessus) : cette story n'y ajoute que la soumission du job thermique, la Story 3.6 y ajoutera la soumission du job A4/PDF. Ne pas anticiper cette extension par une abstraction (pas d'interface `DepositAction` ou de liste de callbacks) — un simple appel supplémentaire dans la même méthode suffira pour la Story 3.6, conformément à la philosophie du projet (pas d'abstraction avant le besoin réel).

### Concurrence — numérotation séquentielle

**Conception finale, différente du plan initial** : un compteur persistant (`Edition.nextSellerNumber`, `SellerProfile.nextItemNumber`), pas `MAX(sellerNumber/itemNumber) + 1`.

Le plan initial (MAX+1 sur les lignes existantes) a été écrit avant implémentation et s'est révélé **bugué** : un test d'intégration (`create_two_sellers_assigns_sequential_seller_numbers`) l'a démontré concrètement — créer vendeur A (n°1), vendeur B (n°2), supprimer B (FR-021, autorisé en phase Dépôt sans article), puis créer vendeur C donnait `MAX(sellerNumber) = 1` (B a disparu) donc `sellerNumber = 2`, **réutilisant** le numéro du vendeur supprimé. Une réutilisation de `sellerNumber`/`itemNumber` viole directement FR-026 (unicité du code-barres pour la durée de vie de l'édition) — deux articles physiques différents porteraient le même code-barres. Le même risque existe pour `itemNumber` via la suppression d'article (FR-024).

Solution retenue : un compteur qui n'est **jamais recalculé depuis les lignes survivantes**, seulement incrémenté :
- `Edition.nextSellerNumber` (colonne `next_seller_number`, défaut 1) : `SellerService.create()` verrouille l'édition (`editionRepository.lockById`), lit `nextSellerNumber`, l'assigne au nouveau vendeur, puis incrémente et sauvegarde l'édition (entité déjà managée par la transaction, pas de `save()` explicite nécessaire — Hibernate détecte la modification).
- `SellerProfile.nextItemNumber` (colonne `next_item_number`, défaut 1) : même principe, verrou sur le vendeur (`sellerRepository.lockById`) avant lecture/incrémentation. Dans `LotService.create()`, le verrou est pris **une seule fois avant la boucle** d'articles (pas par article) — la variable locale du compteur est incrémentée en mémoire à chaque article, puis le compteur final est réécrit sur l'entité `SellerProfile` managée après la boucle (une seule écriture, pas une par article).
- `EditionRepository.lockById`/`SellerRepository.lockById` : verrou pessimiste, même pattern que `EditionCategoryRepository.lockById` déjà en place (Story 3.2) :
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM Edition e WHERE e.id = :id")
  Optional<Edition> lockById(@Param("id") Long id);
  ```
- La migration backfille `next_seller_number`/`next_item_number` à `1 + MAX(existant)` par édition/vendeur, pour les lignes déjà présentes avant cette story (dev/démo).

### Migration `017-item-and-seller-numbering.xml`

Numéro utilisé : `017` (dernier existant avant cette story : `016-printers.xml`). Ajout de colonnes **nullable d'abord** + backfill, car `seller_profiles`/`items` peuvent déjà contenir des lignes (dev/démo) créées par les Stories 3.1-3.3 avant que cette story n'existe. Quatre colonnes au total (deux de plus que le plan initial — voir § Concurrence ci-dessus) :

```xml
<changeSet id="017-seller-item-numbering" author="pluribourse">
    <addColumn tableName="seller_profiles">
        <column name="seller_number" type="INT"/>
    </addColumn>
    <addColumn tableName="items">
        <column name="item_number" type="INT"/>
    </addColumn>
    <addColumn tableName="editions">
        <column name="next_seller_number" type="INT" defaultValueNumeric="1">
            <constraints nullable="false"/>
        </column>
    </addColumn>
    <addColumn tableName="seller_profiles">
        <column name="next_item_number" type="INT" defaultValueNumeric="1">
            <constraints nullable="false"/>
        </column>
    </addColumn>
    <sql>
        UPDATE seller_profiles sp
        SET sp.seller_number = (
            SELECT ranked.rn FROM (
                SELECT id, ROW_NUMBER() OVER (PARTITION BY edition_id ORDER BY id) AS rn
                FROM seller_profiles
            ) ranked
            WHERE ranked.id = sp.id
        );
    </sql>
    <sql>
        UPDATE items i
        SET i.item_number = (
            SELECT ranked.rn FROM (
                SELECT id, ROW_NUMBER() OVER (PARTITION BY seller_profile_id ORDER BY id) AS rn
                FROM items
            ) ranked
            WHERE ranked.id = i.id
        );
    </sql>
    <sql>
        UPDATE editions e
        SET e.next_seller_number = 1 + COALESCE(
            (SELECT MAX(sp.seller_number) FROM seller_profiles sp WHERE sp.edition_id = e.id), 0
        );
    </sql>
    <sql>
        UPDATE seller_profiles sp
        SET sp.next_item_number = 1 + COALESCE(
            (SELECT MAX(i.item_number) FROM items i WHERE i.seller_profile_id = sp.id), 0
        );
    </sql>
    <addNotNullConstraint tableName="seller_profiles" columnName="seller_number" columnDataType="INT"/>
    <addNotNullConstraint tableName="items" columnName="item_number" columnDataType="INT"/>
</changeSet>
```
`ROW_NUMBER() OVER (PARTITION BY ...)` est supporté nativement par MariaDB 11 (dev/prod, `.docker/docker-compose.yml`) et par H2 (tests). La forme `UPDATE ... JOIN ... SET` (syntaxe MySQL/MariaDB) a été essayée en premier et **rejetée par H2** ("Erreur de syntaxe... attendu SET") ; la sous-requête corrélée ci-dessus est la forme portable retenue, validée par l'exécution réelle des tests d'intégration.

### Rendu ESC/POS et code-barres

- **Encodage** : `new Code128Writer().encode(item.getBarcode(), BarcodeFormat.CODE_128, widthPx, heightPx)` → `BitMatrix` → conversion en image raster ESC/POS (commande `GS v 0`). `Code128Writer` directement plutôt que `MultiFormatWriter` (écart mineur par rapport au plan initial) : le format est fixé, l'abstraction multi-format n'apporte rien.
- **Largeur** : `Printer.widthMm` (57 ou 80, existant depuis la Story 3.4) pilote la largeur du raster (384/576 dots) — pas de calibrage DPI précis (aucun AC ne l'exige).
- **Contrat `PrintJob`** (`org.pluribourse.print.service.PrintJob`, Story 3.4) : `void execute(Printer printer)` — toute erreur (port série indisponible, écriture échouée) est **levée**, jamais catchée localement, car `PrinterQueueHandle.consume()` capture déjà `Throwable` pour suspendre la file. `ThermalPrintService.buildDepositJob(sellerProfile, items, locale)` retourne une **lambda** implémentant `PrintJob` (pas une classe Spring qui "implémenterait" l'interface sur le singleton — chaque job porte des données propres à cet appel).
- **Ouverture du port série** : ouverte pour la durée du job puis refermée (`try`-`finally`), jamais gardée ouverte entre deux jobs — même modèle que `SerialPrinterConnectivityChecker` (Story 3.4).
- **⚠️ Bug réel découvert en test — `MessageFormat` et arguments numériques** : `MessageSource.getMessage(key, args, locale)` délègue à `java.text.MessageFormat`, qui applique un `NumberFormat` **sensible à la locale** à tout argument `{0}` de type `Number` sans type de format explicite dans le motif (ex. `{0,number}`). Passer directement le `BigDecimal` du prix du lot ou l'`Integer` du numéro de table produisait un résultat reformaté (perte de précision décimale possible, séparateur `,`/`.` dépendant de la locale) — un test d'intégration l'a détecté (`"Prix du lot : 12.00"` absent du rendu). **Tous les arguments numériques sont pré-formatés en `String`** (`String.format(Locale.ROOT, "%.2f", ...)`, `String.valueOf(...)`) avant d'être passés à `getMessage()`, pour un rendu déterministe indépendant de la locale de formatage implicite. À retenir pour toute future story ajoutant un message paramétré avec un nombre (ex. Story 3.6, bordereau PDF).

### Chargement eager — associations lazy et thread consommateur de la file

**Piège de production réel, pas seulement un artefact de test** : `PrintJob.execute()` s'exécute sur le thread consommateur dédié de `PrinterQueueHandle` (Story 3.4), potentiellement bien après la fin de la transaction/session Hibernate qui a chargé les entités. Si `ThermalLabelRenderer.renderLabel()` déréférence une association `@ManyToOne(fetch = LAZY)` non initialisée (`item.getEdition()`, `item.getSellerProfile()`, `item.getLot()`) à ce moment-là, Hibernate lève `LazyInitializationException` — **en production, pas seulement en test**. La requête `ItemRepository.findAllBySellerProfileIdOrderByItemNumberAsc` utilisée par `DepositValidationService` **doit** `JOIN FETCH` ces trois associations pour que les entités capturées dans la closure du `PrintJob` restent utilisables une fois la session fermée. Toute future story qui construit un `PrintJob` à partir d'entités JPA doit appliquer le même principe : charger tout ce qui sera lu par le job **avant** que la transaction d'origine ne se termine.

### Séparateurs — vendeur et article

FR-030 distingue deux séparateurs, aucun des deux n'a de contenu textuel spécifié au-delà du séparateur vendeur :

- **Séparateur vendeur** (« nom + édition ») : contenu explicite dans l'AC — nom complet du vendeur (prénom + nom, **uniquement** sur ce séparateur, pas sur les étiquettes d'articles individuelles — c'est la seule pièce du rouleau qui identifie le vendeur ; à ce titre elle est destinée à être détachée par le bénévole et non conservée avec les articles, cohérent avec la contrainte RGPD de l'AC4) + nom de l'édition.
- **Séparateur article** (entre chaque étiquette) : ni `epics.md` ni la PRD ne précisent son contenu — seul le terme apparaît dans FR-030. Implémentation par défaut recommandée : une simple commande de coupe partielle ESC/POS (`GS V 1`) sans contenu textuel, dont le seul rôle est de permettre au bénévole de séparer physiquement les étiquettes à la main sans ciseaux. **Signaler ce choix en review** — si un contenu textuel était attendu (ex. numéro d'article suivant), ce n'est documenté nulle part dans les artefacts disponibles.

### i18n documents — `MessageSource`

`messages.properties`/`messages_fr.properties`/`messages_en.properties` existent depuis la Story 1.6 mais ne contiennent que `app.name` — cette story est la **première** à réellement peupler ce canal (distinct de `ngx-translate`, qui ne couvre que l'interface). Résoudre le `Locale` depuis `edition.getDocumentLanguage()` (`Language.FR`/`Language.EN` → `Locale.FRENCH`/`Locale.ENGLISH`), jamais depuis la locale de la requête HTTP ou le compte du bénévole. Clés proposées (à adapter si un nom plus cohérent apparaît en implémentation) :
```
print.label.category=Catégorie
print.label.incomplete=/!\\ INCOMPLET
print.label.table=Table n°{0}
print.label.lotPrice=Prix du lot : {0}€
print.label.lotIndivisible=Lot indivisible : {0}/{1}
```

### MapStruct — champs non exposés aux DTOs

`sellerNumber` (sur `SellerProfile`) et `itemNumber` (sur `Item`) ne doivent **pas** être ajoutés à `SellerDto`/`ItemDto` — aucun AC ne demande de les exposer au frontend, seul le rendu serveur des étiquettes en a besoin. `mapstruct.version` n'a pas de `unmappedTargetPolicy` configuré (défaut `WARN`, pas `ERROR`) : laisser ces champs non mappés dans `SellerMapper.toEntity()`/`ItemMapper.toEntity()` ne casse pas la compilation ; `SellerService.create()`/`ItemService.create()`/`LotService.create()` les positionnent explicitement après le mapping, avant `save()`.

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/item/entity/Item.java` — ajouter `itemNumber` (Integer, not null) et la méthode calculée `getBarcode()`
- `pluribourse-backend/src/main/java/org/pluribourse/seller/entity/SellerProfile.java` — ajouter `sellerNumber` (Integer, not null)
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java` — `create()` doit assigner `itemNumber` avant `save()`, avec verrou vendeur
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/LotService.java` — la boucle de création d'articles doit aussi assigner `itemNumber` (voir Dev Notes § Concurrence pour l'ordre du verrou)
- `pluribourse-backend/src/main/java/org/pluribourse/seller/service/SellerService.java` — `create()` doit assigner `sellerNumber` avant `save()`, avec verrou édition
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java` — ajouter la méthode publique `isAvailable(Long printerId)` (extraite de la logique déjà écrite dans `PrinterSelectionService.isAvailable()`) ; ne pas modifier `submit()`/`registerPrinter()` existants
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterSelectionService.java` — `getSelectedPrinterId(session, PrinterType.THERMAL)` est le seul point d'accès à utiliser pour résoudre l'imprimante sélectionnée (déjà documenté comme tel dans son JavaDoc) ; sa méthode privée `isAvailable(Printer)` doit être remplacée par un appel à `printQueueService.isAvailable(printer.getId())` (déduplication, voir tâche dédiée)
- `pluribourse-backend/src/main/java/org/pluribourse/item/repository/ItemRepository.java` — ajouter les 3 méthodes listées dans la tâche « numérotation vendeur & article », ne pas toucher aux méthodes existantes (`findTableNumberBySellerProfileIdAndCategoryId`, `countByTableNumber` restent utilisées telles quelles par `TableAssignmentService`)

### Project Structure Notes

- Alignement avec `architecture.md` : `print/` accueille `ThermalPrintService`/`ThermalLabelRenderer` (déjà prévus dans l'arborescence indicative, ligne 628) ; `DepositValidationService` n'est **pas** prévu explicitement dans l'arborescence indicative — le placer dans `item/service/` si son rôle principal est l'orchestration de la validation (résolution vendeur/phase/articles), ou dans `print/service/` s'il est traité comme un déclencheur d'impression. Recommandation : `item/service/` (le concept métier "dépôt validé" appartient au domaine vendeur/article, l'impression n'est qu'une conséquence) — mais signaler ce choix en review, car ni les ACs ni `architecture.md` ne le tranchent explicitement.
- Aucune variance détectée par ailleurs : le module `print/` et `item/` suivent la même structure en couches (controller/dto/entity/exception/mapper/repository/service) que tout le reste du projet.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.5 (lignes 1190-1218)]
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#FR-026, FR-027, FR-028, FR-030, FR-032]
- [Source: _bmad-output/planning-artifacts/architecture.md (lignes 64, 129, 257-262, 627-632, 777-778, 817)]
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md#Flow 1 — Dépôt d'un vendeur (lignes 258-270)]
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-deposit.html (lignes 846-860)]
- [Source: _bmad-output/implementation-artifacts/3-3-creation-et-gestion-des-lots.md#Dev Notes § Scope (lignes 93-98) — confirme que la génération de code-barres n'existe pas encore]
- [Source: _bmad-output/implementation-artifacts/3-4-infrastructure-dimpression-registre-dimprimantes-et-files-dynamiques.md — `PrintQueueService`, `PrinterQueueHandle`, `PrintJob`, `SerialPrinterConnectivityChecker`]
- [Source: _bmad-output/implementation-artifacts/3-9-selection-dimprimante-par-le-benevole-a-la-connexion.md — `PrinterSelectionService.getSelectedPrinterId`, contrat explicite pour les Stories 3.5/3.6]
- ZXing `core`/`javase` 3.5.4 — dernière version stable sur Maven Central au moment de la rédaction (vérifié via recherche web, juillet 2026)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `mvnw.cmd -q test -Dtest=ThermalLabelPrintingIT` → 14/14 passed (après 3 itérations : migration `UPDATE ... JOIN ... SET` rejetée par H2 → sous-requête corrélée ; `LazyInitializationException` sur `SellerProfile`/`Edition` → `JOIN FETCH` ; réutilisation de `sellerNumber` après suppression → compteur persistant ; corruption du prix du lot par `MessageFormat` → arguments pré-formatés en `String`)
- `mvnw.cmd test` (suite backend complète) → 239/239 passed, BUILD SUCCESS, aucune régression
- `npm test` (suite frontend complète, Vitest) → 369/369 passed, 46/46 fichiers de test, aucune régression
- `npx tsc --noEmit -p tsconfig.app.json` → aucune erreur

### Completion Notes List

- **Deux bugs de conception réels découverts et corrigés grâce au TDD (E2E), pas seulement des artefacts de test** :
  1. Réutilisation de `sellerNumber`/`itemNumber` après suppression d'un vendeur/article — le plan initial (`MAX(existant)+1`) recalcule depuis les lignes survivantes, donc un numéro libéré par une suppression était réattribué à la création suivante, violant l'unicité du code-barres exigée par FR-026. Corrigé par un compteur persistant (`Edition.nextSellerNumber`, `SellerProfile.nextItemNumber`), jamais recalculé depuis les données existantes. Voir Dev Notes § Concurrence.
  2. `PrintJob.execute()` s'exécute sur le thread consommateur de la file (Story 3.4), après la fin de la transaction ayant chargé les entités — un accès à une association `@ManyToOne(fetch = LAZY)` non initialisée (`item.getEdition()`, `item.getSellerProfile()`, `item.getLot()`) y lève `LazyInitializationException` **en production**, pas seulement en test. Corrigé par `JOIN FETCH` dans `ItemRepository.findAllBySellerProfileIdOrderByItemNumberAsc`. Voir Dev Notes § Chargement eager.
- **Un bug de formatage réel découvert en test** : `MessageFormat` (utilisé par `MessageSource.getMessage()`) applique un `NumberFormat` sensible à la locale à tout argument `{0}` de type `Number` sans type de format explicite — passer directement un `BigDecimal`/`Integer` corrompait silencieusement le texte affiché (le test attendant "Prix du lot : 12.00" ne le trouvait plus). Corrigé en pré-formatant tous les arguments numériques en `String` avant `getMessage()`. Voir Dev Notes § Rendu ESC/POS.
- `ThermalLabelPrintingIT` (14 tests) suit le même écart déjà accepté en Story 3.4/3.9 : aucune imprimante THERMAL ne peut passer sa vérification de connectivité réelle dans cet environnement (pas de port série physique) — utilisé volontairement pour exercer la branche "imprimante indisponible" (AC3), le contenu réel des étiquettes est vérifié par appel direct sur le bean `ThermalLabelRenderer` (même justification que `PrintInfrastructureIT` : contexte Spring réel, pas un test unitaire isolé).
- `escpos-coffee` (candidate de `architecture.md`) volontairement non ajouté — commandes ESC/POS construites à la main, `Code128Writer` de ZXing utilisé directement plutôt que `MultiFormatWriter` (le format est fixé, pas de valeur ajoutée à l'abstraction multi-format).
- Décisions de scope à confirmer en review (documentées dans les Dev Notes, non tranchées unilatéralement) : (1) pas de bouton de réimpression des étiquettes dans cette story — attend la fiche vendeur de la Story 3.6 ; (2) `DepositValidationService` placé dans `item/service/` plutôt que `print/service/` ; (3) contenu du séparateur article (coupe ESC/POS sans texte, faute de spécification).

### File List

- `pluribourse-backend/pom.xml` (modifié — dépendances ZXing `core`/`javase` 3.5.4)
- `pluribourse-backend/src/main/resources/db/changelog/017-item-and-seller-numbering.xml` (nouveau)
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` (modifié — inclusion de la migration 017)
- `pluribourse-backend/src/main/resources/messages.properties` (modifié — clés `print.label.*`)
- `pluribourse-backend/src/main/resources/messages_fr.properties` (modifié — clés `print.label.*`)
- `pluribourse-backend/src/main/resources/messages_en.properties` (modifié — clés `print.label.*`)
- `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/Edition.java` (modifié — champ `nextSellerNumber`)
- `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java` (modifié — `lockById`)
- `pluribourse-backend/src/main/java/org/pluribourse/seller/entity/SellerProfile.java` (modifié — champs `sellerNumber`/`nextItemNumber`)
- `pluribourse-backend/src/main/java/org/pluribourse/seller/repository/SellerRepository.java` (modifié — `lockById`)
- `pluribourse-backend/src/main/java/org/pluribourse/seller/service/SellerService.java` (modifié — assignation `sellerNumber` via compteur verrouillé)
- `pluribourse-backend/src/main/java/org/pluribourse/seller/controller/SellerController.java` (modifié — endpoint `POST /{id}/deposit/validate`)
- `pluribourse-backend/src/main/java/org/pluribourse/item/entity/Item.java` (modifié — champ `itemNumber`, méthodes `getBarcode()`/`getFormattedBarcode()`)
- `pluribourse-backend/src/main/java/org/pluribourse/item/repository/ItemRepository.java` (modifié — `findAllBySellerProfileIdOrderByItemNumberAsc` avec `JOIN FETCH`, `findAllByLotIdOrderById`)
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java` (modifié — assignation `itemNumber` via compteur verrouillé)
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/LotService.java` (modifié — idem, verrou unique avant boucle)
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/DepositValidationService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/ThermalLabelRenderer.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/ThermalPrintService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java` (modifié — méthode `isAvailable(Long)`)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterSelectionService.java` (modifié — délégation à `PrintQueueService.isAvailable`)
- `pluribourse-backend/src/test/java/org/pluribourse/print/ThermalLabelPrintingIT.java` (nouveau)
- `pluribourse-frontend/src/app/services/deposit.service.ts` (nouveau)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts` (modifié — bouton de validation du dépôt)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html` (modifié — bouton de validation du dépôt)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.scss` (modifié — style du bouton)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts` (modifié — tests du bouton)
- `pluribourse-frontend/public/i18n/fr.json` (modifié — clés `volunteer.deposit.button/success/error.*`)
- `pluribourse-frontend/public/i18n/en.json` (modifié — clés `volunteer.deposit.button/success/error.*`)

## Change Log

- 2026-07-20 : Implémentation complète de la Story 3.5 (numérotation séquentielle vendeur/article, génération de code-barres Code 128 via ZXing, rendu ESC/POS des étiquettes thermiques, action « Valider le dépôt » de bout en bout). Deux bugs de conception réels corrigés en cours d'implémentation (réutilisation de numéro après suppression, `LazyInitializationException` sur le thread consommateur de la file), voir Completion Notes. 239/239 tests backend et 369/369 tests frontend passent, aucune régression.
- 2026-07-21 : Re-review de la Story 3.5 (revue triple couche, avec vérification factuelle des affirmations contestées par compilation/exécution réelle du code). 6 patches appliqués : borne à 9999 sur les compteurs vendeur/article avec garde à la création (`TooManyItemsException`/`TooManySellersException`), timeout sur l'écriture du port série thermique, bloc `<rollback>` manquant sur la migration 017, test manquant pour la garde dépôt vide, prix d'article individuel routé via `MessageSource`, boîte de confirmation avant réimpression complète du dépôt. 241/241 tests backend et 370/370 tests frontend passent, aucune régression.
