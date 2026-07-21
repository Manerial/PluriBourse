---
baseline_commit: db9b6ec
---

# Story 3.6: Génération & Impression automatique du bordereau de dépôt PDF

Status: done

## Story

As a bénévole complétant un dépôt,
I want qu'un bordereau de dépôt soit automatiquement imprimé à la validation,
so that le vendeur dispose d'un justificatif papier de ce qu'il a déposé et du montant qu'il percevra, sans étape manuelle supplémentaire.

## Acceptance Criteria

1. À la validation du dépôt (`POST /api/sellers/{id}/deposit/validate`), **en plus** de la mise en file du job d'étiquettes thermiques (Story 3.5, inchangée), un second job PDF est construit et mis en file d'attente vers l'imprimante A4 sélectionnée en session (`PrinterSelectionService.getSelectedPrinterId(session, PrinterType.A4)`) — les deux jobs sont soumis lors du **même appel**, aucun ne déclenche l'autre (FR-031, épic ligne 1229).
2. Si aucune imprimante A4 n'est sélectionnée en session, ou si elle est actuellement indisponible, l'appel échoue en 422 (`InvalidPrinterSelectionException`, réutilisée — même type d'erreur que pour l'imprimante thermique) **avant** que quoi que ce soit ne soit mis en file. Les deux sélections (thermique + A4) doivent être vérifiées **avant** de soumettre le premier job : ne jamais imprimer un rouleau d'étiquettes si le bordereau ne pourra pas suivre, et inversement.
3. Le PDF est généré via **OpenPDF 3.0.5** (packages `org.openpdf.text` / `org.openpdf.text.pdf` — voir Dev Notes § OpenPDF, `com.lowagie` n'existe plus depuis la 3.0.0), dans la langue documentaire de l'édition (`Edition.documentLanguage`), résolue en `Locale` exactement comme en Story 3.5 (jamais la locale de la requête HTTP ni celle du compte bénévole).
4. Le contenu liste chaque article standard sur une ligne (nom, prix unitaire) ; **chaque lot apparaît sur une seule ligne** (nom du lot, prix global du lot), quel que soit le nombre d'articles qu'il contient (FR-031).
5. Le PDF affiche le taux de commission de l'édition et le **reversement net attendu** = somme(prix des articles standalone + prix globaux des lots) − commission, précis au centime (`BigDecimal`, jamais `float`/`double` — contrainte projet).
6. Le job PDF est envoyé à l'imprimante A4 déjà enregistrée (réseau TCP — voir Dev Notes § « USB » vs réseau) via `PrintQueueService.submit`, même mécanisme file/consommateur que le thermique (Story 3.4) : pas de nouveau type de file, pas de nouvelle abstraction.
7. Depuis la fiche vendeur (`/volunteer/deposit`, **en phase Dépôt ou Post-vente**), un bouton « Réimprimer le bordereau » régénère et remet en file **uniquement le PDF** (pas les étiquettes thermiques) — nouvelle action serveur distincte de « Valider le dépôt » (qui reste réservée à la phase Dépôt, inchangée).

## Tasks / Subtasks

- [x] Backend — dépendance PDF (AC: 3)
  - [x] `pom.xml` : ajouter `com.github.librepdf.openpdf:openpdf:3.0.5` (dernière version stable au 2026-07, supersède la baseline `3.0.0` citée par `architecture.md`/FR-031 — voir Dev Notes § OpenPDF). **Correction du groupId pendant l'implémentation** : `com.github.librepdf:openpdf` (sans le second `.openpdf`) résout vers un POM agrégateur vide depuis le split modulaire d'OpenPDF — aucune classe `org.openpdf.*` disponible avec ce groupId. Vérifié en inspectant le jar téléchargé (`jar tf`) avant d'écrire le renderer. Placé à côté du bloc de commentaire ZXing existant, même style de commentaire de justification.
- [x] Backend — rendu du bordereau PDF (AC: 3, 4, 5)
  - [x] Nouvelle classe `DepositSlipRenderer` (`org.pluribourse.domain.print.service`, sibling de `ThermalLabelRenderer`) : `byte[] renderSlip(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale)`. Groupe les `items` en lignes : un article `item.getLot() == null` → une ligne (nom, prix) ; un lot → **une seule ligne** par `lot.getId()` distinct rencontré (nom du lot, prix global), les articles suivants du même lot sont ignorés pour l'affichage (même snapshot `items` déjà chargé par `DepositValidationService`, pas de nouvelle requête — même raisonnement que `ThermalLabelRenderer.lotPosition()`, Story 3.5 Dev Notes § Chargement eager).
  - [x] Calcul du reversement net : `total = Σ(prix article standalone) + Σ(prix global de chaque lot distinct)` ; `net = total.subtract(total.multiply(commissionRate).divide(BigDecimal.valueOf(100))).setScale(2, RoundingMode.HALF_UP)`. Toujours `BigDecimal`, jamais de conversion `double` intermédiaire.
  - [x] Nouvelles clés `print.slip.*` dans `messages.properties` / `messages_fr.properties` / `messages_en.properties` (même mécanisme `MessageSource` que Story 3.5, **pas** ngx-translate) : titre du document, en-tête colonnes, libellé commission, libellé reversement net. Arguments numériques pré-formatés en `String` avant `getMessage()`. **Note** : la formulation initiale de `print.slip.commission` avait deux placeholders `{0}`/`{1}` pour un seul argument fourni (commission monétaire jamais calculée séparément) — corrigé en `Commission rate: {0}%` (un seul placeholder, le taux).
- [x] Backend — transport A4 réseau (AC: 1, 6)
  - [x] Nouvelle classe `DocumentPrintService` (`org.pluribourse.domain.print.service`, nom déjà prévu par `architecture.md` ligne 628) : `PrintJob buildDepositSlipJob(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale)` → retourne une **lambda** `printer -> ...` (même contrat que `ThermalPrintService.buildDepositJob`, pas une classe qui « implémenterait » `PrintJob` sur un singleton Spring).
  - [x] Le job ouvre une `Socket` vers `printer.getHost()`/`printer.getPort()`, écrit les octets du PDF rendu par `DepositSlipRenderer`, puis ferme — borné par un timeout via `CompletableFuture` + `ExecutorService` mono-thread daemon, **même technique exacte** que `ThermalPrintService.printWithTimeout`. Pas de mécanisme dupliqué dans une classe utilitaire séparée.
- [x] Backend — extension de la validation du dépôt (AC: 1, 2)
  - [x] `DepositValidationService.validateDeposit(...)` : résout et valide **les deux** sélections (thermique + A4) via `printerSelectionService.getSelectedPrinterId(session, PrinterType.THERMAL|A4)` + `printQueueService.isAvailable(...)` **avant** tout appel à `printQueueService.submit(...)` — fail-fast sur la première indisponible. Puis soumet le job thermique (inchangé) et le job PDF (nouveau), dans le même appel. Pas d'abstraction `DepositAction`/liste de callbacks introduite — un second appel `submit()` dans la même méthode suffit.
- [x] Backend — réimpression du bordereau seul (AC: 7)
  - [x] `PhaseGuard` : ajout de `requireDepositOrPostSalePhase(Edition edition)` (nouvelle méthode, `requireDepositPhase` existant non modifié) — lève `DepositReprintNotAllowedException` si la phase n'est ni `DEPOSIT` ni `POST_SALE`.
  - [x] Nouvelle exception `DepositReprintNotAllowedException extends BusinessException` (422, code `deposit-reprint-not-allowed`), sur le modèle exact d'`EmptyDepositException`/`ItemModificationNotAllowedException`.
  - [x] `DepositValidationService.reprintDepositSlip(Long sellerProfileId, HttpSession session)` : édition active → `requireDepositOrPostSalePhase` → résolution vendeur (`editionScopedLookup.findSellerInEdition`) → chargement des articles (`ItemRepository.findAllBySellerProfileIdOrderByItemNumberAsc`, réutilisée telle quelle) → `EmptyDepositException` réutilisée si vide → résolution/validation de l'imprimante A4 uniquement (pas de thermique) → soumission du seul job PDF.
  - [x] Nouvel endpoint `POST /api/sellers/{id}/deposit/slip/reprint` sur `SellerController` → 204, même style que l'endpoint existant.
- [x] Frontend — bouton de réimpression (AC: 7)
  - [x] `deposit.service.ts` : ajout de `reprintDepositSlip(sellerProfileId): Observable<void>` (POST vers le nouvel endpoint), même style que `validateDeposit`.
  - [x] **Décision de routage prise pendant l'implémentation, à confirmer en review** (voir Dev Notes § Garde de route) : `depositPhaseGuard` étendu pour autoriser `DEPOSIT` et `POST_SALE` — nom conservé (pas de renommage en `sellerFileGuard`, jugé non trompeur pour l'instant). **Complément non explicitement demandé par la story mais nécessaire à AC7** : `resolveVolunteerLandingPath` (utilisée par le redirect réactif d'`AppLayoutComponent` sur changement de phase SSE) devait aussi accepter `POST_SALE`, sans quoi un bénévole déjà sur `/volunteer/deposit` en aurait été éjecté vers `/404` dès le passage de phase Dépôt → Post-vente, rendant le bouton de réimpression inatteignable après une transition de phase en direct. Signalé en review comme demandé par la story pour ce point de routage.
  - [x] `deposit-page.component.ts`/`.html` : bouton « Réimprimer le bordereau » visible dès que `items().length > 0`, dans les deux phases ; le bouton « Valider le dépôt » conditionné à la phase Dépôt via `CurrentEditionService`. Passe par `ConfirmDialogService` avant l'appel. Toast succès/erreur dédié.
  - [x] i18n `fr.json`/`en.json` : nouvelles clés `volunteer.deposit.button.reprintSlip`, `success.reprintSlip`, `error.reprintSlip`, `reprintDialog.title/description`. **`error.printerUnavailable` non réutilisée** : son texte ("Aucune imprimante thermique...") n'est pas générique — remplacée par deux clés dédiées, `error.printersUnavailable` (validation combinée thermique+A4, message générique) et `error.a4PrinterUnavailable` (réimpression, A4 uniquement).
- [x] Tests backend (AC: 1–7)
  - [x] Nouvelle classe `DepositSlipPrintingIT` (`org.pluribourse.domain.print`, même style que `ThermalLabelPrintingIT`/`PrintInfrastructureIT`). **Limitation d'environnement documentée dans la classe** : le thermique ne peut jamais passer son contrôle de connectivité (pas de matériel série réel), et `validateDeposit()` vérifie le thermique avant l'A4 — impossible en pratique de tester via HTTP le scénario « les deux jobs partent » ou « A4 indisponible alors que le thermique est disponible ». Couvert à la place : 422 sans aucune sélection, 422 avec A4 valide sélectionné mais thermique absent, 422 avec thermique sélectionné mais indisponible (malgré A4 valide) ; réimpression bloquée hors Dépôt/Post-vente (`deposit-reprint-not-allowed`) ; réimpression en Post-vente réussie (A4 seul, thermique jamais vérifié) ; dépôt vide (`empty-deposit`) ; réimpression sans A4 sélectionné (`invalid-printer-selection`) ; contenu du PDF (ligne de lot unique, montant net) vérifié par appel direct sur `DepositSlipRenderer` ; livraison réelle des octets PDF à un socket TCP joignable vérifiée par appel direct sur `DocumentPrintService`.
- [x] Tests frontend
  - [x] `deposit-page.component.spec.ts` : bouton de réimpression visible/désactivé selon état, toasts succès/erreur, visibilité conditionnelle du bouton « Valider le dépôt » selon la phase.
  - [x] Test du guard étendu (`deposit-phase.guard.spec.ts`) : autorise `POST_SALE` en plus de `DEPOSIT`. Ajout d'un test dans `app-layout.component.spec.ts` couvrant le fix du redirect réactif (Dépôt → Post-vente ne redirige plus vers `/404`).

### Review Findings

Revue adversariale à 3 couches (Blind Hunter — diff seul, Edge Case Hunter — diff + accès projet, Acceptance Auditor — diff + spec) sur les modifications non commitées. Acceptance Auditor : conforme, 0 finding. 19 findings bruts remontés par Blind Hunter/Edge Case Hunter, fusionnés, triés et résolus ci-dessous : 4 patches appliqués, 1 décision utilisateur déférée, 1 décision utilisateur patchée, 6 différés (pré-existants/hors périmètre), 6 rejetés comme bruit ou faux positifs vérifiés. 256/256 tests backend et 380/380 tests frontend passent après application des correctifs.

- [x] [Review][Defer] Nombres non localisés dans le bordereau PDF (virgule française absente) — `commissionRate.toPlainString()`/`net.toPlainString()` produisent toujours un point décimal ("10.00") même en locale FR. `DepositSlipRenderer.java` (méthode `renderSlip`). **Décision utilisateur (2026-07-21) : laisser tel quel** — cohérent avec `ThermalLabelRenderer` (Story 3.5) qui utilise déjà `Locale.ROOT`/`%.2f` pour les prix quelle que soit la langue du document ; ne pas diverger entre les deux documents imprimés pour la même donnée.
- [x] [Review][Patch] Pas d'exclusion mutuelle ni de verrouillage anti-double-clic entre « Valider le dépôt » et « Réimprimer le bordereau » [`deposit-page.component.ts`/`.html`] — **Décision utilisateur (2026-07-21) : corriger les deux boutons maintenant.** Appliqué : le signal de verrouillage (`validatingDeposit`/`reprintingSlip`) est désormais activé dès le clic (avant l'ouverture de la boîte de dialogue de confirmation, pas après), et chaque action est bloquée si l'autre est déjà en cours ; les deux boutons se désactivent mutuellement dans le template. Touche aussi `validateDeposit()` (Story 3.5, déjà "done") pour cohérence des deux actions. Deux nouveaux tests couvrant le blocage croisé.
- [x] [Review][Patch] `computeNetPayout` : `RoundingMode` explicite manquant sur la division intermédiaire par 100 [`DepositSlipRenderer.java:135-138`] — appliqué (scale 4 + `HALF_UP` explicite sur la division, avant l'arrondi final à 2 décimales). Nouveau test `deposit_slip_renderer_rounds_net_amount_half_up_at_an_exact_tie` (Order 15) exerçant une véritable égalité d'arrondi (9.9250 → HALF_UP 9.93 vs HALF_EVEN 9.92), absente jusqu'ici (le seul cas testé, 19.00 à 10 %, ne nécessitait aucun arrondi réel).
- [x] [Review][Patch] `DocumentPrintService` : `new Socket(host, port)` sans timeout de connexion explicite, contrairement à `NetworkPrinterConnectivityChecker` — un réseau/imprimante en black-hole bloquerait le thread daemon bien au-delà du timeout de 10 s annoncé (`CompletableFuture.get()` arrête l'attente, pas la tâche) [`DocumentPrintService.java:59-67`] — appliqué (connexion bornée à 2000 ms via `Socket().connect(InetSocketAddress, timeout)`, même borne que `NetworkPrinterConnectivityChecker`).
- [x] [Review][Patch] Fuite de ressource dans `DepositSlipPrintingIT` Order(8) : `Executors.newSingleThreadExecutor()` jamais fermé [`DepositSlipPrintingIT.java` Order 8] — appliqué (try-with-resources, `ExecutorService` implémente `AutoCloseable` depuis Java 19).
- [x] [Review][Defer] Soumission non atomique thermique+A4 : si le job thermique est soumis avec succès puis que la soumission A4 échoue (rarissime, `submit()` n'utilise qu'une `LinkedBlockingDeque` non bornée), le rouleau part sans garantie que le bordereau suive [`DepositValidationService.java`] — deferred, architecture `PrintQueueService` (Story 3.4) explicitement hors périmètre de modification pour cette story.
- [x] [Review][Defer] Scénario HTTP « les deux jobs partent »/« A4 indisponible alors que le thermique est disponible » jamais exercé de bout en bout — deferred, limitation d'environnement documentée (pas de matériel série réel en CI), déjà acceptée depuis la Story 3.5.
- [x] [Review][Defer] Aucun test avec des caractères hors CP1252 dans les noms vendeur/article (risque de glyphes manquants/mutilés) [`DepositSlipRenderer.java`] — deferred, limitation systémique déjà acceptée pour tout le sous-système d'impression (`ThermalLabelRenderer` utilise aussi un charset Latin fixe, pas de support Unicode).
- [x] [Review][Defer] Pas de test dédié vérifiant le rejet par rôle sur `POST /{id}/deposit/slip/reprint` [`SellerController.java`] — deferred, protection déjà assurée de façon centrale par `SecurityConfig` (règle générique authentifié+non-SELLER), même niveau de couverture que l'endpoint `deposit/validate` existant, non testé spécifiquement non plus.
- [x] [Review][Defer] Bouton « Réimprimer le bordereau » sans garde de phase côté client (seul `items().length > 0` conditionne son affichage) [`deposit-page.component.html`] — deferred, le backend est déjà l'autorité (rejet 422 vérifié par test), impact UX mineur seulement en cas de fenêtre de transition de phase.
- [x] [Review][Defer] `ExecutionException`/`getCause()` potentiellement `null` dans `DocumentPrintService.printWithTimeout` — deferred, réplique exacte d'un pattern déjà en production dans `ThermalPrintService` (Story 3.5), reproduit ici sur instruction explicite des Dev Notes.

**Dismissed (bruit ou faux positifs vérifiés) :** LazyInitializationException sur `sellerProfile.getEdition()` côté file d'attente réelle — vérifié empiriquement infondé (`EditionScopedLookup.findSellerInEdition` initialise déjà le proxy dans la transaction ; test `reprint_deposit_slip_in_post_sale_phase_with_a4_selected_succeeds`, Order 14, renforcé pour le prouver via un job marqueur, 256/256 tests passent) · message d'exception `DepositReprintNotAllowedException` non traduit — conforme à toutes les autres `BusinessException` du projet (jamais routées via `MessageSource`, le code d'erreur est la seule donnée traduite côté frontend) · duplication du pattern timeout/executor entre `DocumentPrintService` et `ThermalPrintService` — copié-collé explicitement demandé par les Dev Notes · risque d'orphelinage de la clé i18n `printerUnavailable` renommée — vérifié, aucune référence restante dans le dépôt · absence de propriété Maven pour la version d'OpenPDF / note de licence — cohérent avec la convention existante du `pom.xml` (versions inline sauf réutilisation multiple), licence LGPL/MPL déjà compatible avec les préférences du projet.

## Dev Notes

### Point de départ — ce qui existe déjà (ne pas réinventer)

Cette story **étend** une infrastructure déjà livrée par les Stories 3.4/3.5/3.9, elle ne crée pas de nouveau système de file d'attente :

- `PrintQueueService`/`PrinterQueueHandle`/`PrintJob` (Story 3.4, `org.pluribourse.domain.print.service`) : file + thread consommateur par imprimante, `submit(Long printerId, PrintJob job)`, `isAvailable(Long printerId)`. **Ne pas modifier** leur contrat.
- `PrinterSelectionService.getSelectedPrinterId(HttpSession, PrinterType)` (Story 3.9) : seul point d'accès pour résoudre l'imprimante sélectionnée en session — déjà documenté comme contrat pour les Stories 3.5/3.6 dans son propre JavaDoc.
- `DepositValidationService.validateDeposit(Long sellerProfileId, HttpSession session)` (Story 3.5, `org.pluribourse.domain.item.service`) est **le point d'entrée unique** déjà préparé pour cette story — son JavaDoc dit explicitement : *"Single entry point for 'valider le dépôt' (FR-028) — the point of extension for story 3.6, which will add its own PDF job submission here once it exists."* Ajouter la soumission du job PDF **dans cette méthode**, ne pas créer un second endpoint qui dupliquerait la résolution vendeur/phase/articles.
- `ThermalPrintService`/`ThermalLabelRenderer` (Story 3.5) sont le **modèle direct** à suivre pour `DocumentPrintService`/`DepositSlipRenderer` : même séparation (un renderer qui produit des `byte[]`, un service qui construit la `PrintJob` et gère le transport avec timeout), même style de lambda, même charge de tests (E2E via contrôleurs + appel direct au bean de rendu pour vérifier le contenu).
- `Item.getLot()`, `Lot.getName()`/`getGlobalPrice()` : chaque article de lot référence déjà son `Lot` (chargé eager depuis la Story 3.5, voir plus bas) — pas besoin d'une nouvelle requête `LotRepository` pour regrouper l'affichage.

### OpenPDF — changement de package majeur (3.0.0 → aucune compatibilité `com.lowagie`)

`architecture.md` (lignes 128, 168, 262) cite « OpenPDF 3.0.0 », mais **toute documentation/tutoriel utilisant `com.lowagie.text.*` est obsolète pour cette version** : à partir d'OpenPDF 2.4.0 les classes ont été dupliquées dans `org.openpdf.*`, et depuis la **3.0.0** l'ancien paquet `com.lowagie` est **entièrement supprimé** — seul `org.openpdf.text`/`org.openpdf.text.pdf` existe. Vérifié par recherche web (juillet 2026) : dernière version stable publiée = **3.0.5** (23 mai 2026), recommandée ici plutôt que la baseline 3.0.0 citée par l'architecture (patch releases seulement, aucun changement de package entre les deux). Toute recherche de documentation OpenPDF pendant l'implémentation doit filtrer sur `org.openpdf`, pas `com.lowagie`.

```xml
<dependency>
    <groupId>com.github.librepdf.openpdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>3.0.5</version>
</dependency>
```

**Correction apportée pendant l'implémentation** : le groupId `com.github.librepdf` (sans le second `.openpdf`) résout vers un POM agrégateur vide depuis le split modulaire d'OpenPDF (confirmé en inspectant `jar tf` sur le jar téléchargé — 0 classe) ; le groupId correct portant les classes `org.openpdf.*` est `com.github.librepdf.openpdf`.

### Polices OpenPDF — éviter la substitution automatique en police Unicode embarquée

`new Font(Font.HELVETICA, size, style)` (sans `BaseFont` explicite) bascule silencieusement vers une police CID Unicode embarquée (`LiberationSans`, encodage `Identity-H`) dès qu'un caractère hors single-byte standard est rendu — ici, le signe **€**. Une fois basculé, le texte du flux de contenu PDF n'est plus composé de caractères mais d'index de glyphes, ce qui casse à la fois la lisibilité par un futur outil d'extraction de texte et gonfle inutilement chaque PDF d'une police TTF entière embarquée pour un seul caractère. Construire les `Font` via `BaseFont.createFont(BaseFont.HELVETICA[_BOLD], BaseFont.CP1252, BaseFont.NOT_EMBEDDED)` puis `new Font(baseFont, size)` force l'encodage CP1252/WinAnsi standard et évite la substitution.

### « Imprimante A4/USB » (architecture.md) vs imprimante réseau TCP (code réel, Story 3.4)

`architecture.md` ligne 262 dit *"PDF généré par OpenPDF 3.0.0 → envoyé à l'imprimante USB"*. **Ce libellé est obsolète/trompeur** : la Story 3.4 (déjà livrée) a implémenté les imprimantes A4 comme des imprimantes **réseau TCP**, conformément à FR-077 — `Printer.host`/`Printer.port` (défaut 9100), vérifiées par `NetworkPrinterConnectivityChecker` via `Socket`/`InetSocketAddress`. Il n'existe **aucune** infrastructure USB dans le code (`pluribourse-backend/src/main/java/org/pluribourse/domain/print/`). Cette story doit suivre le code réel déjà en place : `DocumentPrintService` ouvre une `Socket` TCP vers `printer.getHost():printer.getPort()` et y écrit les octets du PDF — exactement comme `ThermalPrintService` écrit sur le port série, mais avec un flux TCP à la place. Ne pas tenter d'implémenter un vrai transport USB : ce n'est demandé par aucun AC vérifiable et casserait la cohérence avec l'enregistrement d'imprimante A4 existant (Story 3.4/3.8, host+port).

### Ordre de validation des deux imprimantes — fail-fast avant tout envoi

`ThermalLabelPrintingIT` (Story 3.5) a déjà un scénario `validate_deposit_without_thermal_printer_selected_returns_422`. Cette story ajoute une seconde imprimante obligatoire (A4) au même appel — **les deux sélections doivent être vérifiées avant que le premier `submit()` ne soit appelé**. Sans cette précaution, un dépôt avec thermique disponible mais A4 absente imprimerait quand même le rouleau d'étiquettes (irréversible — pas d'annulation possible une fois le job consommé) alors que l'appel HTTP renverrait 422. Structurer `validateDeposit()` ainsi : résoudre+valider thermique, résoudre+valider A4, *puis* soumettre thermique, *puis* soumettre PDF — pas d'entrelacement résolution/soumission entre les deux imprimantes.

### Regroupement des lots sur le bordereau — différent du rendu thermique

Attention à ne **pas** copier telle quelle la logique de `ThermalLabelRenderer` : sur une étiquette thermique, **chaque article** d'un lot reçoit sa propre étiquette (avec position X/N, Story 3.5 AC5). Sur le bordereau PDF, l'AC de l'épic est explicite : *"un lot apparaît sur une seule ligne"* — dédupliquer par `item.getLot().getId()`, n'afficher qu'une fois `lot.getName()` + `lot.getGlobalPrice()`, quel que soit le nombre d'articles membres. Utiliser un `LinkedHashSet<Long>` (ou équivalent) des ids de lot déjà vus en itérant `items` (déjà trié par `itemNumber`, cohérent avec Story 3.5) pour préserver un ordre déterministe.

### Chargement eager — même piège que la Story 3.5

`DocumentPrintService.buildDepositSlipJob(...)` retourne une lambda dont `execute(Printer)` s'exécute sur le thread consommateur de `PrinterQueueHandle`, **après** la fin de la transaction ayant chargé les entités (même mécanisme que `ThermalPrintService`, Story 3.5 Dev Notes § Chargement eager). `ItemRepository.findAllBySellerProfileIdOrderByItemNumberAsc` (déjà `JOIN FETCH edition`/`sellerProfile`/`lot` depuis la Story 3.5) est réutilisée telle quelle par `reprintDepositSlip()` — ne pas écrire une nouvelle requête sans les mêmes `JOIN FETCH`, sous peine de `LazyInitializationException` réelle en production sur le thread de la file.

### Réimpression du bordereau — nouvelle action, pas une réutilisation de `validateDeposit`

Contrairement à ce qu'on pourrait supposer, l'endpoint existant `POST /api/sellers/{id}/deposit/validate` **ne peut pas** servir de base pour la réimpression : il appelle `PhaseGuard.requireDepositPhase(edition)`, qui interdit la phase Post-vente, et il soumet **toujours** le job thermique en plus du PDF — or l'AC de l'épic pour la réimpression ne mentionne que le bordereau (*"le bordereau est régénéré et remis en file d'attente"*, pas les étiquettes). D'où la nouvelle méthode `reprintDepositSlip()` + le nouveau `PhaseGuard.requireDepositOrPostSalePhase()`, volontairement distincts de la validation de dépôt.

### Garde de route frontend — décision à signaler en review

La page `/volunteer/deposit` (seule vue "fiche vendeur" existante) est aujourd'hui bloquée hors phase Dépôt par `depositPhaseGuard` (`pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts`), y compris sur navigation directe/favori (AC7 de la Story 3.9 — comportement volontaire). L'AC de cette story exige que la réimpression du bordereau soit accessible **aussi en Post-vente**, ce qui implique d'assouplir ce guard. Recommandation : étendre `depositPhaseGuard` pour accepter `DEPOSIT` et `POST_SALE` plutôt que créer une route dédiée qui dupliquerait tout l'affichage de la fiche vendeur (liste d'articles, recherche vendeur) — mais aucun artefact (`epics.md`, `EXPERIENCE.md`) ne tranche explicitement ce choix de routage : **signaler cette décision en review**, comme la Story 3.5 l'a fait pour un point similaire.

### Hors périmètre de cette story

Le bouton « Réimprimer le bordereau » ne réimprime **pas** les étiquettes thermiques (hors scope, cohérent avec le texte de l'AC de l'épic qui ne mentionne que le bordereau). Si l'utilisateur souhaite un bouton unique réimprimant les deux à la fois, le signaler en review — l'extension serait triviale (`thermalPrintService.buildDepositJob(...)` existe déjà et pourrait être appelée depuis la même méthode), mais n'est demandée par aucun AC de cette story.

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/DepositValidationService.java` — ajouter la résolution/soumission A4 dans `validateDeposit()`, ajouter la méthode `reprintDepositSlip(...)`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java` — ajouter `requireDepositOrPostSalePhase`, ne pas toucher `requireDepositPhase`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/seller/controller/SellerController.java` — ajouter l'endpoint `POST /{id}/deposit/slip/reprint`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java` — **aucune modification attendue** (contrat `submit`/`isAvailable` déjà suffisant)
- `pluribourse-backend/pom.xml` — ajouter la dépendance OpenPDF
- `pluribourse-backend/src/main/resources/messages.properties`/`messages_fr.properties`/`messages_en.properties` — nouvelles clés `print.slip.*`
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts` — assouplir pour `DEPOSIT`/`POST_SALE` (voir Dev Notes § Garde de route)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts`/`.html` — bouton de réimpression + visibilité conditionnelle du bouton de validation
- `pluribourse-frontend/src/app/services/deposit.service.ts` — nouvelle méthode `reprintDepositSlip`

### Migrations

Aucune migration Liquibase requise par cette story : aucune nouvelle colonne ni table (le PDF est régénéré à la volée depuis les données existantes, jamais persisté — même philosophie que « valider le dépôt », Story 3.5, qui ne persiste aucun flag).

### Project Structure Notes

- Alignement avec `architecture.md` ligne 628 : `DocumentPrintService.java` est le nom **déjà prévu** dans l'arborescence indicative du module `print/service/` — l'utiliser tel quel plutôt qu'un autre nom. Attention : cette arborescence indicative n'a pas suivi le refactor `db9b6ec` ("Clean code : use domain package") et omet le segment `domain/` — le code réel fait foi, package effectif `org.pluribourse.domain.print.service`.
- `DepositSlipRenderer` n'est pas nommé explicitement dans `architecture.md` (seul `DocumentPrintService` y figure) — nommage par analogie directe avec `ThermalLabelRenderer`/`ThermalPrintService` (Story 3.5), même découpage rendu/transport.
- Aucune variance structurelle par ailleurs : `print/` et `item/` gardent la même organisation en couches (controller/dto/entity/exception/mapper/repository/service) que le reste du projet.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.6 (lignes 1220-1240)]
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#FR-031, FR-076, FR-077, FR-079]
- [Source: _bmad-output/planning-artifacts/architecture.md (lignes 128, 168, 171, 257-262, 626-632, 817)]
- [Source: _bmad-output/implementation-artifacts/3-4-infrastructure-dimpression-registre-dimprimantes-et-files-dynamiques.md — `PrintQueueService`, `PrinterQueueHandle`, `PrintJob`, `NetworkPrinterConnectivityChecker`]
- [Source: _bmad-output/implementation-artifacts/3-5-generation-impression-des-etiquettes-thermiques.md#Dev Notes § Point d'extension Story 3.6, § Chargement eager, § Rendu ESC/POS — patterns directement réutilisés]
- [Source: _bmad-output/implementation-artifacts/3-9-selection-dimprimante-par-le-benevole-a-la-connexion.md — `PrinterSelectionService.getSelectedPrinterId`]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/DepositValidationService.java — JavaDoc actuel désignant explicitement cette story comme point d'extension]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/ThermalPrintService.java, ThermalLabelRenderer.java — modèle direct pour DocumentPrintService/DepositSlipRenderer]
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintInfrastructureIT.java — technique `ServerSocket(0)` pour simuler une imprimante A4 réellement joignable en test]
- OpenPDF 3.0.5 (com.github.librepdf:openpdf), dernière version stable Maven Central au 2026-07 — [Releases · LibrePDF/OpenPDF](https://github.com/LibrePDF/OpenPDF/releases), migration de package `com.lowagie` → `org.openpdf` confirmée depuis la 3.0.0 (vérifié via recherche web, juillet 2026)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `mvnw.cmd -q test -Dtest=DepositSlipPrintingIT` → 14/14 passed (après 3 corrections : groupId Maven OpenPDF erroné → jar sans classes ; substitution automatique de police Unicode embarquée dès l'utilisation du signe € → `BaseFont` CP1252 explicite ; `LazyInitializationException` sur `SellerProfile.edition` dans le test de livraison socket direct, faute d'avoir initialisé le proxy avant le passage sur le thread de la file — même piège que documenté en Story 3.5 § Chargement eager, corrigé côté test)
- `mvnw.cmd -q test` (suite backend complète) → 255/255 passed, BUILD SUCCESS, aucune régression
- `npm test` (suite frontend complète, Vitest) → 378/378 passed, 46/46 fichiers de test, aucune régression

### Completion Notes List

- **Un bug de coordonnées Maven découvert avant toute compilation** : le groupId `com.github.librepdf` recommandé par les Dev Notes de la story résout vers un POM agrégateur vide depuis le split modulaire d'OpenPDF (0 classe dans le jar téléchargé, vérifié par `jar tf`) — corrigé en `com.github.librepdf.openpdf`, seul groupId portant réellement les classes `org.openpdf.*`.
- **Un bug réel de police PDF découvert en testant le rendu** : `new Font(Font.HELVETICA, ...)` sans `BaseFont` explicite bascule silencieusement en police CID Unicode embarquée (glyphes, pas caractères) dès que le signe € est rendu — corrigé via `BaseFont.createFont(..., BaseFont.CP1252, BaseFont.NOT_EMBEDDED)`. Sans ce correctif, chaque bordereau aurait embarqué une police TTF complète pour un seul caractère, et le contenu texte serait devenu non extractible par un futur outil (recherche, accessibilité). Voir Dev Notes § Polices OpenPDF.
- **Un bug de placeholders de message découvert avant exécution** : `print.slip.commission` avait deux placeholders `{0}`/`{1}` mais un seul argument était jamais fourni (aucun montant de commission n'est calculé séparément) — corrigé en un message à un seul placeholder (le taux). `MessageFormat` n'aurait pas levé d'exception, juste laissé `{1}` litéral dans chaque bordereau imprimé.
- **Décisions prises pendant l'implémentation, à confirmer en review** (documentées dans les Dev Notes, non tranchées unilatéralement) :
  1. `depositPhaseGuard` étendu à `DEPOSIT`/`POST_SALE` sans renommage (`sellerFileGuard` jugé non nécessaire).
  2. `resolveVolunteerLandingPath` (utilisée par le redirect réactif d'`AppLayoutComponent`) également étendue à `POST_SALE` — non explicitement demandé par la story, mais nécessaire pour qu'AC7 survive à un changement de phase SSE pendant qu'un bénévole est déjà sur `/volunteer/deposit` (sans ce complément, le redirect réactif l'aurait éjecté vers `/404`).
  3. Clé i18n `error.printerUnavailable` non réutilisée pour la réimpression (texte non générique, spécifique au thermique) — remplacée par `error.printersUnavailable` (validation combinée) et `error.a4PrinterUnavailable` (réimpression), et le message de succès/description de la validation combinée mis à jour pour mentionner le bordereau en plus des étiquettes.
- `DepositSlipPrintingIT` (14 tests) documente dans son propre Javadoc une limitation d'environnement héritée de la Story 3.5 : aucune imprimante THERMAL ne peut jamais passer sa vérification de connectivité réelle ici, et `validateDeposit()` vérifie le thermique avant l'A4 — les scénarios HTTP « les deux jobs partent » et « A4 indisponible alors que le thermique est disponible » ne sont donc pas atteignables via l'API réelle dans cet environnement. AC1/AC6 (job PDF réellement construit, mis en file, livré) sont à la place vérifiés par appel direct sur les beans `DocumentPrintService`/`DepositSlipRenderer` réels (même justification déjà acceptée pour `PrintInfrastructureIT`/`ThermalLabelPrintingIT`).
- Contenu du bordereau : nom du vendeur et nom de l'édition affichés en en-tête (signature de `renderSlip`/`buildDepositSlipJob` prenant déjà `SellerProfile` en paramètre, à l'image du séparateur thermique) — non explicitement listé par l'AC de contenu de l'épic, mais nécessaire pour qu'un document remis en main propre au vendeur soit identifiable ; à signaler en review si un format différent est souhaité.

### File List

- `pluribourse-backend/pom.xml` (modifié — dépendance OpenPDF 3.0.5)
- `pluribourse-backend/src/main/resources/messages.properties` (modifié — clés `print.slip.*`)
- `pluribourse-backend/src/main/resources/messages_fr.properties` (modifié — clés `print.slip.*`)
- `pluribourse-backend/src/main/resources/messages_en.properties` (modifié — clés `print.slip.*`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java` (modifié — `requireDepositOrPostSalePhase`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/DepositReprintNotAllowedException.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/DepositValidationService.java` (modifié — soumission du job A4 dans `validateDeposit`, nouvelle méthode `reprintDepositSlip`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/seller/controller/SellerController.java` (modifié — endpoint `POST /{id}/deposit/slip/reprint`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` (nouveau)
- `pluribourse-frontend/src/app/services/deposit.service.ts` (modifié — `reprintDepositSlip`)
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts` (modifié — autorise `DEPOSIT`/`POST_SALE`)
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.spec.ts` (modifié — cas `POST_SALE`)
- `pluribourse-frontend/src/app/models/active-phase.enum.ts` (modifié — `resolveVolunteerLandingPath` autorise `POST_SALE`)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` (modifié — test du redirect réactif Dépôt → Post-vente)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts` (modifié — bouton/action de réimpression, visibilité conditionnelle du bouton de validation)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html` (modifié — idem)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.scss` (modifié — style des boutons d'action)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts` (modifié — tests du bouton de réimpression et de la visibilité conditionnelle)
- `pluribourse-frontend/public/i18n/fr.json` (modifié — clés `volunteer.deposit.button/success/error/reprintDialog.*`)
- `pluribourse-frontend/public/i18n/en.json` (modifié — idem)

## Change Log

- 2026-07-21 : Implémentation complète de la Story 3.6 (génération PDF du bordereau de dépôt via OpenPDF, soumission du job A4 en parallèle du job thermique à la validation du dépôt, action de réimpression du bordereau seul accessible en phase Dépôt et Post-vente). Trois bugs réels corrigés en cours d'implémentation (groupId Maven OpenPDF erroné, substitution automatique de police Unicode embarquée par le signe €, placeholders de message incohérents), voir Completion Notes. 255/255 tests backend et 378/378 tests frontend passent, aucune régression.
- 2026-07-21 : Code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor). Acceptance Auditor conforme (0 finding). 4 patches appliqués (RoundingMode explicite sur le calcul du reversement net + nouveau test d'arrondi HALF_UP à l'égalité, timeout de connexion explicite sur le socket A4, fuite d'`ExecutorService` dans un test, verrouillage anti-double-clic/exclusion mutuelle entre « Valider le dépôt » et « Réimprimer le bordereau » — décision utilisateur, touche aussi `validateDeposit()` de la Story 3.5). 1 décision utilisateur déférée (nombres non localisés dans le PDF, cohérent avec Story 3.5). 6 findings différés (pré-existants ou hors périmètre, voir `deferred-work.md`). 6 findings rejetés après vérification empirique (dont un risque de `LazyInitializationException` sur le thread de la file d'attente, infirmé en renforçant le test `reprint_deposit_slip_in_post_sale_phase_with_a4_selected_succeeds`). 256/256 tests backend et 380/380 tests frontend passent après correctifs. Statut passé à `done`.
