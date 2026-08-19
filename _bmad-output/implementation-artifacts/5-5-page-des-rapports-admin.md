---
baseline_commit: 92f35a6352605b2b4ee9f11d85ca6fc7b6ef9f15
---

# Story 5.5: Page des rapports admin

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant qu'administrateur,
je veux une page de rapports qui n'affiche que les sections pertinentes pour la phase courante,
afin d'agir rapidement sans naviguer parmi des options non pertinentes.

## Contexte important : la majeure partie de cette story est déjà livrée

Les Stories 5.3 et 5.4 ont délibérément anticipé la structure de `/admin/reports` (voir leurs Dev
Notes : "évite de refaire deux fois le routing/guard/403"). Aujourd'hui :

- La route `/admin/reports` existe, protégée par le rôle ADMIN côté serveur (`@PreAuthorize` classe
  `AdminReportController`), sans garde de phase dédiée côté route (comportement délibéré, voir Dev
  Notes § Écart routage).
- `ReportPageComponent` (`pluribourse-frontend/src/app/features/report/`) affiche déjà, via
  `effect()` réactif sur `CurrentEditionService.currentEdition()` :
  - **Bilan journalier** (phase Vente uniquement, bouton "Actualiser" + bouton "Imprimer") — AC 1
    de cette story est donc **déjà satisfaite**, aucune modification requise sur ce bloc.
  - **Bilan d'édition** (phase Post-vente + Clôturée, bouton "Imprimer") — carte `stat-grid` avec
    articles vendus/invendus, recettes brutes totales, commission totale, ventilation par moyen de
    paiement.
- Le patron "section complètement absente, jamais grisée" (AC 3) est déjà en place via les trois
  blocs `@if` mutuellement exclusifs de `report-page.component.html`.

**Le travail réel de cette story** porte sur deux lacunes identifiées en comparant epics.md AC 2 à
`EXPERIENCE.md` (composant "Page Rapports", ligne ~149) :

1. La carte "Bilan d'édition" actuelle ne montre pas les deux métriques que l'EXPERIENCE.md décrit
   pour la section synthèse : **total reversements nets** et **total recettes association**. Seules
   `grossRevenue` et `commission` existent aujourd'hui côté DTO.
2. **FR-091/FR-092** (export CSV catalogue + reversements, Post-vente + Clôturée, admin uniquement,
   téléchargement direct) — fonctionnalité entièrement absente du code aujourd'hui (confirmé par
   recherche : aucune dépendance CSV dans `pom.xml`, aucun endpoint d'export).

## Acceptance Criteria

1. **Étant donné** que l'édition est en phase Vente, **quand** l'admin navigue vers `/admin/reports`,
   **alors** seule la section bilan journalier est affichée avec un bouton "Actualiser" **et** les
   sections synthèse et export sont absentes. *(Déjà satisfait — aucune modification.)*
2. **Étant donné** que l'édition est en phase Post-vente ou Clôturée, **quand** l'admin navigue vers
   `/admin/reports`, **alors** la section synthèse est visible (total des ventes, total reversements
   nets, total recettes association) en lecture seule **et** deux boutons d'export CSV apparaissent
   ("Exporter le catalogue" et "Exporter les reversements") **et** cliquer sur un export CSV
   déclenche un téléchargement de fichier direct sans boîte de dialogue.
3. **Étant donné** qu'une phase ne correspond pas à la condition de disponibilité d'une section de
   rapport, **quand** l'admin consulte la page des rapports, **alors** cette section est
   complètement absente (pas grisée — absente). *(Déjà satisfait pour bilan journalier/synthèse —
   s'applique aussi au nouveau bloc export à construire dans cette story.)*

## Tasks / Subtasks

- [x] **Task 1 — Backend : étendre le bilan de synthèse avec reversements nets + recettes
      association (AC 2)**
  - [x] Dans `SettlementService` (`pluribourse-backend/.../domain/payout/service/SettlementService.java`),
        extraire la logique de `getSettlements()` (hors résolution d'édition et garde de phase) dans
        une nouvelle méthode publique `List<SettlementDto> getSettlementsForEdition(Edition edition)`,
        appelée par `getSettlements()` elle-même après sa garde `PhaseGuard.requirePostSalePhase`
        existante. Ne change **rien** au comportement de `getSettlements()`/`/settlements` (reste
        strictement Post-vente, FR-095) — cette extraction sert uniquement à permettre la réutilisation
        pour l'export CSV (Task 2), pas à élargir l'accès existant.
  - [x] **Décision actée avec l'utilisateur (2026-08-19)** : "recettes de l'association" inclut, en
        plus de la commission, (a) l'intégralité du montant dû pour tout vendeur "Non réclamé"
        (FR-052) **et** (b) l'écart entre le montant dû et le montant réellement remis pour tout
        vendeur soldé à un montant inférieur au montant dû (FR-051, cas explicitement autorisé par
        l'AC "le bénévole peut tout de même confirmer"). Dans `SettlementService`, ajouter une
        nouvelle méthode `BigDecimal getAssociationRetainedTotal(Edition edition)` :
        ```java
        List<Settlement> settlements = settlementRepository.findAllBySellerProfileEditionId(edition.getId());
        Map<Long, List<Item>> soldItemsBySellerId = itemRepository.findAllByEditionIdAndSoldTrue(edition.getId()).stream()
                .collect(Collectors.groupingBy(i -> i.getSellerProfile().getId()));
        BigDecimal retained = BigDecimal.ZERO;
        for (Settlement settlement : settlements) {
            SellerProfile seller = settlement.getSellerProfile();
            BigDecimal total = ItemPricing.computeTotal(soldItemsBySellerId.getOrDefault(seller.getId(), List.of()));
            BigDecimal amountDue = ItemPricing.computeNetPayout(total, edition.getCommissionRate());
            BigDecimal paidToSeller = settlement.getStatus() == SettlementStatus.UNCLAIMED ? BigDecimal.ZERO : settlement.getAmount();
            retained = retained.add(amountDue.subtract(paidToSeller));
        }
        return retained.setScale(2, RoundingMode.HALF_UP);
        ```
        **Important — piège de performance déjà rencontré sur ce module (revue Story 5.1) :**
        calculer `amountDue` via un appel par vendeur à la méthode privée existante
        `computeAmountDue(seller, edition)` (qui interroge `itemRepository` par vendeur) réintroduirait
        exactement le scan N+1 par vendeur que la revue de code de la Story 5.1 avait corrigé
        ("requête scoping vendeur pour éviter un scan complet d'édition"). Reproduire à la place le
        même patron **déjà batché** que `getSettlements()` : une seule requête
        `itemRepository.findAllByEditionIdAndSoldTrue` groupée par vendeur, puis lookup O(1) dans la
        boucle — comme dans le bloc de code ci-dessus. Pertinent vu NFR-001 (~100 vendeurs de
        référence).
  - [x] Ajouter deux champs à `EditionSummaryReportDto`
        (`pluribourse-backend/.../domain/report/dto/EditionSummaryReportDto.java`), **en fin de
        record, après `cardTotal`** (ordre exact :
        `soldItemCount, unsoldItemCount, grossRevenue, commission, cashTotal, checkTotal, cardTotal,
        netPayoutTotal, associationRevenueTotal`) : `BigDecimal netPayoutTotal` et
        `BigDecimal associationRevenueTotal`. Cette position (en fin de liste, pas entre `commission`
        et `cashTotal`) est ce qui permet au correctif de compilation ci-dessous de se limiter à un
        simple ajout de 2 arguments en fin d'appel, sans réordonner les arguments déjà existants aux
        3 sites d'appel positionnels de `EditionReportPrintingIT`.
  - [x] Dans `ReportService.getEditionReport` (`.../domain/report/service/ReportService.java`),
        injecter `SettlementService` (constructeur Lombok `@RequiredArgsConstructor` déjà en place)
        et calculer :
        - `netPayoutTotal = grossRevenue.subtract(commission)` (mathématiquement exact : le taux de
          commission est unique et figé par édition — FR-016 — donc la somme des reversements nets
          par vendeur égale toujours `grossRevenue - commission_totale`, pas besoin de reparcourir les
          vendeurs un par un).
        - `associationRevenueTotal = commission.add(settlementService.getAssociationRetainedTotal(edition))`.
        - `.setScale(2, RoundingMode.HALF_UP)` sur les deux nouveaux champs, comme le reste de la
          méthode.
  - [x] **Ne pas toucher** `EditionReportRenderer`
        (`.../domain/print/service/EditionReportRenderer.java`) : il ne lit que les champs qu'il cite
        explicitement (`soldItemCount`, `unsoldItemCount`, `grossRevenue`, `commission`,
        `cash/check/cardTotal`) — les deux nouveaux champs du DTO n'apparaîtront pas dans le PDF
        "bilan d'édition", ce qui est correct : FR-055/FR-094 ne les mentionnent pas pour le PDF, ils
        sont un ajout écran-only (`EXPERIENCE.md`, section "Rapport de synthèse").
  - [x] **Compilation cassée à anticiper** : `EditionReportPrintingIT.java` construit
        `EditionSummaryReportDto` positionnellement (constructeur `record`, 7 arguments) à 3 endroits —
        `@Order(14)`, `@Order(15)`, `@Order(16)` (lignes ~383, ~404, ~418 au moment de l'écriture de
        cette story). Ajouter `BigDecimal.ZERO, BigDecimal.ZERO` en fin d'appel à ces 3 sites (ces
        tests exercent uniquement `EditionReportRenderer`, qui ignore les 2 nouveaux champs — la
        valeur exacte n'a aucune importance ici, seule la compilation compte). Ne pas oublier ce
        correctif avant de lancer la suite complète.
  - [x] Étendre `EditionSummaryReportDto` frontend
        (`pluribourse-frontend/src/app/models/edition-summary-report.model.ts`) avec les deux mêmes
        champs (`netPayoutTotal: number`, `associationRevenueTotal: number`).
  - [x] Dans `report-page.component.html`, ajouter deux `stat-tile` dans le `stat-grid` existant de
        la section "Bilan d'édition" (ne pas créer un second bloc `@if` — même carte, mêmes signaux de
        chargement/erreur déjà en place).
  - [x] Nouvelles clés i18n (FR + EN) : `admin.reports.edition.netPayoutTotal`,
        `admin.reports.edition.associationRevenueTotal` — voir libellés proposés ci-dessous.

- [x] **Task 2 — Backend : endpoints d'export CSV (AC 2, FR-091, FR-092)**
  - [x] Créer `ReportExportService` dans `.../domain/report/service/`, deux méthodes :
        `byte[] exportCatalogCsv(Edition edition)` et `byte[] exportSettlementsCsv(Edition edition)`.
        Chaque méthode applique elle-même `PhaseGuard.requirePostSaleOrClosedPhase(edition)` en
        première ligne (même garde déjà utilisée par `ReportService.getEditionReport` — **réutiliser
        l'exception existante `EditionReportNotAllowedException`**, ne pas créer une 5ᵉ/6ᵉ classe
        `*NotAllowedException` : la revue de code de la Story 5.4 a déjà flagué la prolifération de ce
        pattern comme un defer à ne pas aggraver).
  - [x] `exportCatalogCsv` : réutiliser `itemRepository.findAllByEditionIdForCatalog(edition.getId())`
        (déjà scoping-safe par ID d'édition explicite, pas `getActiveEdition()`) +
        `itemMapper.toCatalogDtos(items)` (mapper MapStruct existant, `ItemMapper`). Colonnes CSV :
        nom, code-barres formaté (`ItemCatalogDto.barcode`), catégorie, table, prix, statut
        complet/incomplet, statut vendu/invendu, vendeur (prénom + nom). Un article de lot exporte une
        ligne par article membre (comme le catalogue écran — FR-091 ne demande pas de regroupement par
        lot, contrairement aux PDF).
  - [x] `exportSettlementsCsv` : réutiliser `settlementService.getSettlementsForEdition(edition)`
        (Task 1). Colonnes CSV : nom, prénom, téléphone, email, montant dû, statut (traduire
        `SettlementStatus` UNSETTLED/SETTLED/UNCLAIMED en libellé localisé).
  - [x] En-têtes de colonnes localisés via `MessageSource` selon `edition.getDocumentLanguage()`
        (même résolution `Language.FR ? Locale.FRENCH : Locale.ENGLISH` que
        `EditionSummaryReportPrintService`), nouvelles clés `messages_fr.properties`/
        `messages_en.properties` sous le préfixe `export.catalog.column.*` / `export.settlement.column.*`
        (pas `print.*` — ce n'est pas un job d'impression).
  - [x] Échappement CSV : encadrer **systématiquement** chaque champ de guillemets doubles et doubler
        tout guillemet interne (RFC 4180 simple) — un nom d'article ou un commentaire vendeur peut
        contenir une virgule. Pas besoin d'ajouter de dépendance (Commons CSV/OpenCSV) pour 6-8
        colonnes fixes ; une petite fonction utilitaire suffit.
  - [x] Préfixer le flux de bytes avec le BOM UTF-8 (`﻿`) — sans lui, Excel (cible réaliste des
        bénévoles/association) interprète les caractères accentués (é, è, à) selon l'encodage système
        au lieu d'UTF-8, corrompant l'affichage des noms/catégories.
  - [x] Deux nouveaux endpoints sur `AdminReportController`
        (`.../domain/report/controller/AdminReportController.java`), même patron que
        `/edition/{editionId}` (résolution par ID explicite via `editionService.requireEdition`, pas
        `getActiveEdition()` — nécessaire pour rester correct en Clôturée) :
        - `GET /admin/reports/edition/{editionId}/export/catalog` → `text/csv;charset=UTF-8`,
          `Content-Disposition: attachment; filename="catalogue.csv"`
        - `GET /admin/reports/edition/{editionId}/export/settlements` → `text/csv;charset=UTF-8`,
          `Content-Disposition: attachment; filename="reversements.csv"`

- [x] **Task 3 — Frontend : bloc "Exports CSV" (AC 2, AC 3)**
  - [x] `ReportService` (`pluribourse-frontend/src/app/services/report.service.ts`) : deux nouvelles
        méthodes `exportCatalog(editionId: number): Observable<HttpResponse<Blob>>` et
        `exportSettlements(editionId: number): Observable<HttpResponse<Blob>>`, `GET` avec
        `{ responseType: 'blob', observe: 'response' }`.
  - [x] `ReportPageComponent` : deux signaux `exportingCatalog`/`exportingSettlements` (garde
        anti-double-clic, même patron que `printing`/`printingEditionReport`). Méthodes
        `exportCatalog()`/`exportSettlements()` : garde sur `currentEditionService.currentEdition()`
        nul (même course SSE déjà gérée dans `printEditionReport()`), déclenchent le téléchargement en
        créant un `Blob` URL (`URL.createObjectURL`) + un `<a>` temporaire avec l'attribut `download`,
        cliqué par script puis révoqué (`URL.revokeObjectURL`) — c'est le seul moyen de garder l'état
        spinner + toast pendant l'appel HTTP (un lien `<a href>` direct ne permettrait pas ça et
        enverrait la requête hors du contexte Angular/session).
  - [x] **Piège Angular à anticiper** : avec `responseType: 'blob'`, une erreur serveur (ex. 422 si la
        phase a changé entre l'affichage du bouton et le clic) arrive aussi comme un `Blob` dans
        `error.error`, pas comme du JSON parsé — `extractErrorType()` (`shared/http-error.util.ts`) ne
        fonctionnera pas telle quelle sur une réponse blob. Ne pas tenter de parser le blob d'erreur
        pour distinguer les cas (complexité disproportionnée pour ce bouton) : un seul toast d'erreur
        générique persistant suffit, cohérent avec le reste du fichier.
  - [x] Nouveau bloc `@if (isEditionReportPhase())` (même garde que la section synthèse — ne pas créer
        une 3ᵉ condition de phase) contenant les deux boutons, chacun avec spinner inline pendant le
        chargement et désactivé pendant que l'autre export est en cours n'est **pas** requis (les deux
        exports sont indépendants, contrairement au bouton d'impression global de la Story 5.6) — un
        signal par bouton suffit, pas de garde croisée.
  - [x] Toast succès (4s) "Export téléchargé." après téléchargement déclenché ; toast erreur persistant
        sinon (`UX-DR19`, patron déjà utilisé partout ailleurs dans ce composant).
  - [x] Nouvelles clés i18n (FR + EN) : `admin.reports.export.title`, `admin.reports.export.catalog`,
        `admin.reports.export.settlements`, `admin.reports.export.success`,
        `admin.reports.export.error`.

- [x] **Task 4 — Tests backend**
  - [x] `EditionReportPrintingIT` : **se limiter strictement** au correctif mécanique de compilation
        déjà décrit ci-dessus (2 arguments `BigDecimal.ZERO` en fin des 3 appels positionnels). **Ne
        pas** y ajouter de scénario de solde/vendeur non réclamé pour tester
        `netPayoutTotal`/`associationRevenueTotal` : ce fichier a été lu intégralement pendant la
        préparation de cette story — c'est un storyboard à un seul vendeur (Bob) sur 18 `@Order`, où
        `assertEditionReport()` (une seule méthode privée partagée) est appelée à l'Order 10
        (Post-vente) et à l'Order 11 (Clôturée) spécifiquement pour prouver que **rien ne change**
        entre les deux phases. Bob n'est jamais soldé dans ce fichier (aucune occurrence de
        `/settlements` dans le fichier). Ajouter un second vendeur soldé nécessiterait de lui faire
        déposer et vendre des articles **avant** l'Order 9 (la phase Dépôt/Vente est déjà terminée à
        ce stade) — donc de restructurer les Orders 2 et 5-7 existants, pas seulement d'ajouter des
        étapes à la fin. Risque de régression sur un fichier déjà stable et commenté en détail, pour
        un gain de couverture qui peut être obtenu ailleurs à moindre risque (point suivant).
  - [x] Nouvelle classe `ReportExportIT` dans `org.pluribourse.domain.print` (même package que les
        autres tests de rapports/impression — voir Dev Notes § Convention de package). Construire un
        storyboard **à deux vendeurs dès le départ** (Alice + Bob, tous deux créés/vendus pendant les
        phases Dépôt/Vente de ce nouveau fichier, sans dépendre d'`EditionReportPrintingIT`), soldés
        différemment en Post-vente : Alice marquée "Non réclamé" (`POST /settlements/{id}/unclaimed`),
        Bob soldé avec un montant strictement inférieur au montant dû (`POST /settlements/{id}/settle`,
        FR-051). Cette même fixture à deux vendeurs sert **à la fois** :
        - à vérifier `netPayoutTotal`/`associationRevenueTotal` sur `GET /admin/reports/edition/{id}`
          (`associationRevenueTotal` = commission + montant dû d'Alice (Non réclamé) + écart de solde
          de Bob — calcul exact à vérifier au centime près) — pas besoin de toucher
          `EditionReportPrintingIT` pour obtenir cette couverture ;
        - au contenu de l'export CSV reversements (une ligne par statut : SETTLED, UNCLAIMED — utile
          pour vérifier le libellé localisé de chaque statut).
        Storyboard `@Order` couvrant en plus : export catalogue (contenu CSV exact, en-têtes localisés
        FR, BOM présent, échappement d'un nom d'article contenant une virgule), 422 hors
        Post-vente/Clôturée (`edition-report-not-allowed`, réutilisation confirmée), 403 pour un
        bénévole (garde de classe déjà couverte ailleurs, un seul test de fumée suffit ici), 404 pour
        un ID d'édition inconnu sur les deux endpoints d'export (même patron que `edition_report_
        for_an_unknown_edition_id_returns_404`, `@Order(13)` d'`EditionReportPrintingIT` — comportement
        automatique via `editionService.requireEdition`, mais à vérifier explicitement).

- [x] **Task 5 — Tests frontend**
  - [x] **Compilation cassée à anticiper (frontend, même nature que le point équivalent backend
        Task 1)** : `report-page.component.spec.ts` déclare `const EDITION_REPORT:
        EditionSummaryReportDto = { ... }` (typé explicitement, ~ligne 42) sans les 2 nouveaux champs
        — TypeScript refusera la compilation dès que l'interface les rend obligatoires. Ajouter
        `netPayoutTotal: 14.4, associationRevenueTotal: 1.6` (ou toute valeur cohérente avec le reste
        du fixture) à cet objet en même temps que l'extension de l'interface (Task 1), pas seulement
        au moment d'écrire les nouveaux tests.
  - [x] `report-page.component.spec.ts` : nouveaux tests pour les 2 stat-tiles ajoutés et les 2 boutons
        d'export (état spinner, appel service, toast succès/erreur, garde `currentEdition()` nul).
  - [x] `report.service.spec.ts` : tests pour `exportCatalog`/`exportSettlements` (URL, `responseType:
        'blob'`, méthode GET).

### Review Findings

- [x] [Review][Decision] Sémantique de `netPayoutTotal`/`associationRevenueTotal` face aux vendeurs non soldés — `netPayoutTotal` (`grossRevenue - commission`) représente le montant théorique dû à l'ensemble des vendeurs sous le taux de commission unique, pas le montant réellement versé (FR-051 autorise un solde partiel). `associationRevenueTotal` ne parcourt que les vendeurs ayant une ligne `Settlement` (SOLDÉ/NON RÉCLAMÉ, via `settlementRepository.findAllBySellerProfileEditionId`) — un vendeur encore NON SOLDÉ en Post-vente contribue à `netPayoutTotal` mais à rien dans `associationRevenueTotal`, donc les deux totaux ne s'additionnent à `grossRevenue` que lorsque tous les vendeurs sont traités. **Décision actée avec l'utilisateur (2026-08-19)** : accepté tel quel — comportement conforme à la formule explicitement prescrite par la story ; les deux totaux ne sont cohérents qu'une fois tous les vendeurs soldés, acceptable pour un rapport de synthèse consulté en continu pendant la Post-vente. Aucun changement de code.

- [x] [Review][Patch] Requête redondante dans `getAssociationRetainedTotal` [pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java:83-94] — `ReportService.getEditionReport` charge déjà `soldItems` via `itemRepository.findAllByEditionIdAndSoldTrue(edition.getId())` avant d'appeler `getAssociationRetainedTotal`, qui relance exactement la même requête en interne au lieu de réutiliser la liste déjà chargée. **Corrigé** : `getAssociationRetainedTotal` accepte désormais `List<Item> soldItems` en paramètre, réutilisé par `ReportService`.

- [x] [Review][Patch] Export CSV vulnérable à l'injection de formule Excel [pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportExportService.java:251-254] — `escape()` encadre chaque champ de guillemets mais ne neutralise pas un champ commençant par `=`, `+`, `-` ou `@` ; un nom d'article/vendeur commençant par un de ces caractères devient une formule active à l'ouverture dans Excel, la cible explicitement visée par le commentaire du code. **Corrigé** : une apostrophe est préfixée avant le champ si son premier caractère est `=`, `+`, `-` ou `@`.

- [x] [Review][Patch] `ReportExportIT` ne teste jamais la phase Clôturée [pluribourse-backend/src/test/java/org/pluribourse/domain/print/ReportExportIT.java] — le Javadoc de la classe affirme que les exports sont accessibles en Post-vente/Clôturée, mais aucun `@Order` n'avance l'édition jusqu'à CLOSED pour revérifier les deux endpoints d'export (contrairement à `EditionReportPrintingIT`, qui couvre déjà ce cas pour l'endpoint JSON). **Corrigé** : nouveau `@Order(9)` avance à CLOSED et revérifie le contenu exact des deux exports.

- [x] [Review][Patch] Échappement du guillemet interne jamais testé [pluribourse-backend/src/test/java/org/pluribourse/domain/print/ReportExportIT.java] — seule la virgule interne (`"Robe, rouge"`) est couverte ; aucun champ du storyboard ne contient de guillemet double, donc la branche `replace("\"", "\"\"")` d'`escape()` n'a aucune couverture. **Corrigé** : l'article invendu de Bob renommé `Peluche "XL"`, assertion dédiée à l'échappement du guillemet doublé.

- [x] [Review][Patch] `downloadBlob()` ne rattache jamais le `<a>` temporaire au DOM [pluribourse-frontend/src/app/features/report/report-page.component.ts:994-1001] — `link.click()` est appelé sans `document.body.appendChild(link)`/`removeChild(link)` ; fonctionne sur les navigateurs cibles actuels mais correctif trivial et sans risque pour fiabiliser le téléchargement. **Corrigé** : `appendChild`/`removeChild` ajoutés autour de `click()`.

- [x] [Review][Defer] Duplication du calcul du montant dû entre `getAssociationRetainedTotal` et `getSettlementsForEdition` [pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java:78-89] — deux implémentations indépendantes de la même formule (montant dû), conforme au bloc de code prescrit explicitement par cette story — déféré, pas actionnable sans revoir la décision de la story.

- [x] [Review][Defer] Prix vide sur les lignes d'article de lot dans l'export catalogue [pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportExportService.java:188-189] — `item.price()` est `null` par conception pour un article de lot (le prix réel vit sur `Lot.globalPrice`) ; confirmé que l'écran catalogue existant (`item-catalog.component.html`) a exactement le même comportement — préexistant, non introduit par ce diff.

- [x] [Review][Defer] Redirection `password-change-required` cassée pour les réponses blob [pluribourse-frontend/src/app/core/interceptors/auth.interceptor.ts:17] — `extractErrorType()` lit `error.error.type`, qui est en réalité le type MIME du `Blob` (pas le champ JSON `type`) pour les deux nouveaux endpoints d'export — première utilisation de `responseType: 'blob'` du projet. Un 403 `password-change-required` sur un export tombe dans la branche générique (déconnexion + `/login`) au lieu de `/change-password` directement — cas de bord étroit (nécessite `forcePasswordChange=true` ET un clic sur un bouton d'export), qui s'autocorrige au prochain login via le garde `authGuard`.

## Dev Notes

### `ReportExportIT` — ce qu'il faut et ce qu'il ne faut pas copier des classes voisines

Contrairement à `EditionReportPrintingIT`/`SettlementReportPrintingIT` (qui testent un vrai job
d'impression), l'export CSV ne passe **jamais** par `PrintQueueService`/`PrinterBridgeClient` — c'est
un simple téléchargement HTTP synchrone. **Ne pas copier** le `@DynamicPropertySource`
`PrinterBridgeDouble`, l'enregistrement d'imprimante A4 ni la sélection d'imprimante : rien de tout
ça n'est nécessaire pour ce fichier. En revanche, réutiliser le patron d'authentification par session
(`MockHttpSession` admin + bénévole via `POST /api/auth/login`, voir `@BeforeAll` de
`EditionReportPrintingIT`) et `SettleDto`/`amount` (un seul champ `BigDecimal`) pour l'appel
`POST /api/settlements/{sellerId}/settle`. `/settlements/**` n'est pas sous `/admin/**` et n'a pas de
`@PreAuthorize` (partagé ADMIN + BÉNÉVOLE) — la session admin déjà utilisée pour le reste du
storyboard suffit pour solder/marquer non réclamé.

### Convention de package (déjà établie, à ne pas rediscuter)

Tous les tests E2E des rapports/exports/impressions vivent sous
`org.pluribourse.domain.print` malgré le nom du package (`DailyReportPrintingIT`,
`EditionReportPrintingIT`, `SettlementReportPrintingIT` y sont déjà, et testent aussi bien les
endpoints JSON écran que les jobs d'impression). La Story 5.4 a explicitement choisi de suivre
cette convention plutôt que `domain.report` pour rester cohérente — faire de même ici pour
`ReportExportIT`, même si l'export CSV n'a rien d'un job d'impression.

### Limite architecturale connue (non bloquante, déjà actée sur 2.7/5.1/5.4)

`EditionService.getActiveEdition()` exclut structurellement `CLOSED` de `PhaseType.ACTIVE`. Le
frontend (`CurrentEditionService.currentEdition()`) redevient donc `null` dès qu'une édition passe
en Clôturée — la branche `CLOSED` de `isEditionReportPhase()` (déjà présente dans
`report-page.component.ts`) reste du code mort tant que cette limite n'est pas résolue (Story 2.7).
Les endpoints backend de cette story (résolution par ID explicite via
`editionService.requireEdition`) sont néanmoins corrects et testables en Clôturée dès aujourd'hui —
même situation que le bilan d'édition (Story 5.4) et son bouton d'impression. Ne pas tenter de
"réparer" cette limite dans le cadre de cette story : hors périmètre, déjà déféré.

### Écart routage (EXPERIENCE.md vs implémentation existante)

`EXPERIENCE.md` (ligne ~149) décrit un accès direct par URL en Préparation/Dépôt redirigeant vers
`/admin/editions`. L'implémentation actuelle (Story 5.3, non remise en cause depuis) affiche à la
place l'état vide `admin.reports.emptyPhase` sur place, sans redirection — cohérent avec l'AC 3
d'epics.md ("section absente", pas de mention de redirection). Ne pas ajouter de garde de route
dans cette story : ni epics.md ni le sprint status ne signalent cet écart comme un défaut, et
l'ajouter serait une extension de périmètre non demandée.

### Fichiers à toucher

**Backend :**
- `domain/payout/service/SettlementService.java` — extraction `getSettlementsForEdition`
- `domain/report/dto/EditionSummaryReportDto.java` — 2 champs
- `domain/report/service/ReportService.java` — calcul des 2 champs
- `domain/report/service/ReportExportService.java` — **nouveau**
- `domain/report/controller/AdminReportController.java` — 2 endpoints
- `messages_fr.properties` / `messages_en.properties` — clés `export.catalog.column.*`,
  `export.settlement.column.*`

**Frontend :**
- `models/edition-summary-report.model.ts` — 2 champs
- `services/report.service.ts` — 2 méthodes
- `features/report/report-page.component.ts` — signaux + méthodes export
- `features/report/report-page.component.html` — 2 stat-tiles + nouveau bloc export
- `public/i18n/fr.json`, `public/i18n/en.json` — clés `admin.reports.edition.netPayoutTotal`,
  `admin.reports.edition.associationRevenueTotal`, `admin.reports.export.*`

**Tests :**
- `EditionReportPrintingIT.java` — extension
- `ReportExportIT.java` — **nouveau**
- `report-page.component.spec.ts`, `report.service.spec.ts` — extensions

### Libellés i18n proposés (FR — adapter EN en miroir)

```
admin.reports.edition.netPayoutTotal: "Total reversements nets"
admin.reports.edition.associationRevenueTotal: "Total recettes association"
admin.reports.export.title: "Exports"
admin.reports.export.catalog: "Exporter le catalogue"
admin.reports.export.settlements: "Exporter les reversements"
admin.reports.export.success: "Export téléchargé."
admin.reports.export.error: "Impossible de générer l'export."
```

### Testing standards

E2E par les contrôleurs uniquement (CLAUDE.md) : `ReportExportIT` doit passer par MockMvc réel sur
`AdminReportController`, pas de test de service isolé pour `ReportExportService`. `@TestMethodOrder`
+ `@Order`, storyboard narratif comme les classes voisines. BigDecimal partout pour les montants
(NFR-003) — jamais float/double, y compris dans le calcul CSV.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.5 — Page des rapports admin]
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#F6 — Rapports
  (FR-054, FR-055, FR-057, FR-058, FR-059, FR-091, FR-092, FR-094)]
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md — "Exports
  CSV (FR-091, FR-092)" : couverts par cette story]
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md —
  Component Patterns, "Page Rapports"]
- [Source: _bmad-output/implementation-artifacts/5-3-rapport-de-ventes-journalier-admin.md — décision
  de créer la route/squelette `/admin/reports` en avance]
- [Source: _bmad-output/implementation-artifacts/5-4-bilan-dedition-rapports-des-vendeurs-non-soldes.md
  — patron `PhaseGuard.requirePostSaleOrClosedPhase`, résolution par ID explicite, limite CLOSED]

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story workflow)

### Debug Log References

- Correctif de compilation anticipé appliqué tel que documenté dans la story : `EditionReportPrintingIT` (3 sites positionnels, `EditionSummaryReportDto` 7→9 arguments) et `report-page.component.spec.ts` (fixture `EDITION_REPORT` étendue).
- Écart non anticipé détecté en écrivant `ReportExportIT` : `ItemMapper.toCatalogDto` mappe `barcode` sur `Item.getFormattedBarcode()` (format `SSSS-IIII` avec tiret), pas `getBarcode()` (format `SSSSIIII` sans tiret) — la première version du test utilisait le format sans tiret par erreur de lecture rapide du mapper ; corrigée après le premier échec de test (`0001-0001` au lieu de `00010001`), aucune conséquence sur le code de production.
- Piste explorée puis abandonnée pour le test frontend des 2 nouveaux stat-tiles : un second appel à `fixture.detectChanges()` après la résolution asynchrone de `loadEditionReport()` re-déclenche l'`effect()` du constructeur du composant (le guard `isLoadingEditionReport()` lu de façon synchrone dans `loadEditionReport()`, puis écrit par la même fonction, entre dans le graphe de dépendances de l'effect) — comportement pré-existant du composant (Story 5.3/5.4), non introduit par cette story. Remplacé par une assertion sur `component.editionReport()`, cohérente avec le patron déjà utilisé par les tests existants de cette suite pour le contenu chargé de façon asynchrone (aucun test existant n'affirmait sur le DOM résolu, uniquement sur le signal).

### Completion Notes List

- **Task 1** (bilan de synthèse étendu) : `SettlementService.getSettlementsForEdition` extrait de `getSettlements()` (même comportement, réutilisable) ; nouvelle méthode `getAssociationRetainedTotal` (calcul batché, un seul groupBy vendeur, pas de scan N+1) ; `EditionSummaryReportDto` étendu de 2 champs en fin de record ; `ReportService.getEditionReport` calcule `netPayoutTotal`/`associationRevenueTotal`. `EditionReportRenderer` non modifié (confirmé : n'expose que les champs qu'il cite explicitement). Correctif de compilation des 3 sites positionnels d'`EditionReportPrintingIT` appliqué. Frontend : modèle, template (2 `stat-tile` supplémentaires dans la carte existante), clés i18n FR/EN.
- **Task 2** (endpoints d'export CSV) : nouveau `ReportExportService` (`exportCatalogCsv`/`exportSettlementsCsv`), réutilise `PhaseGuard.requirePostSaleOrClosedPhase` (donc `EditionReportNotAllowedException`, aucune nouvelle classe d'exception), échappement RFC 4180 systématique, BOM UTF-8, en-têtes localisés via `MessageSource`. Deux nouveaux endpoints `GET /admin/reports/edition/{id}/export/{catalog|settlements}` sur `AdminReportController`, résolution par ID explicite (`editionService.requireEdition`).
- **Task 3** (bloc frontend "Exports") : `ReportService.exportCatalog`/`exportSettlements` (`responseType: 'blob'`, `observe: 'response'`) ; `ReportPageComponent` : signaux `exportingCatalog`/`exportingSettlements`, méthode privée `runExport` partagée (garde anti-double-clic + garde `currentEdition()` nul, téléchargement via URL Blob temporaire + `<a download>`), toasts succès/erreur. Nouveau bloc `@if (isEditionReportPhase())` séparé de la carte synthèse (même garde de phase, pas de 3ᵉ condition). i18n FR/EN.
- **Task 4** (tests backend) : `EditionReportPrintingIT` limité au correctif mécanique de compilation, comme prescrit (aucun nouveau scénario métier). Nouvelle classe `ReportExportIT` (package `org.pluribourse.domain.print`, storyboard à deux vendeurs Alice/Bob soldés différemment dès le départ) couvrant : `netPayoutTotal`/`associationRevenueTotal` au centime près, contenu CSV catalogue (BOM, en-têtes FR, échappement d'un nom avec virgule), contenu CSV reversements (un statut Soldé + un Non réclamé), 422 hors Post-vente/Clôturée, 403 bénévole, 404 édition inconnue sur les deux endpoints.
- **Task 5** (tests frontend) : correctif de compilation du fixture `EDITION_REPORT` appliqué. Nouveaux tests sur `report.service.spec.ts` (2 méthodes d'export, `responseType: 'blob'`) et `report-page.component.spec.ts` (garde anti-double-clic, garde `currentEdition()` nul, toasts succès/erreur, présence/absence du bloc export selon la phase, valeurs des 2 nouveaux champs via le signal `editionReport()`).
- 450/450 tests backend, 598/598 tests frontend, build de production frontend sans erreur, aucune régression.
- Vérification visuelle humaine de `/admin/reports` (CLAUDE.md § Interaction utilisateur) non effectuée — `dev-story` a tourné sans supervision interactive, comme pour les stories précédentes de cet épic.

### File List

**Backend :**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/EditionSummaryReportDto.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportExportService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/controller/AdminReportController.java` (modifié)
- `pluribourse-backend/src/main/resources/messages_fr.properties` (modifié)
- `pluribourse-backend/src/main/resources/messages_en.properties` (modifié)

**Backend — tests :**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java` (modifié)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ReportExportIT.java` (nouveau)

**Frontend :**
- `pluribourse-frontend/src/app/models/edition-summary-report.model.ts` (modifié)
- `pluribourse-frontend/src/app/services/report.service.ts` (modifié)
- `pluribourse-frontend/src/app/features/report/report-page.component.ts` (modifié)
- `pluribourse-frontend/src/app/features/report/report-page.component.html` (modifié)
- `pluribourse-frontend/public/i18n/fr.json` (modifié)
- `pluribourse-frontend/public/i18n/en.json` (modifié)

**Frontend — tests :**
- `pluribourse-frontend/src/app/features/report/report-page.component.spec.ts` (modifié)
- `pluribourse-frontend/src/app/services/report.service.spec.ts` (modifié)

## Change Log

- 2026-08-19 — create-story : story créée. La majeure partie de l'AC 1/AC 3 déjà livrée par avance dans les Stories 5.3/5.4 (route `/admin/reports`, blocs `@if` mutuellement exclusifs, patron "section absente"). Périmètre réel identifié en comparant epics.md AC 2 à `EXPERIENCE.md` : extension de la carte "Bilan d'édition" avec `netPayoutTotal`/`associationRevenueTotal` (écran uniquement, PDF inchangé) + export CSV catalogue/reversements (FR-091/FR-092, entièrement neuf). Décision actée avec l'utilisateur sur le périmètre de "recettes de l'association" (Non réclamé + écart de solde partiel). Statut → ready-for-dev.
- 2026-08-19 — dev-story : implémentation complète full-stack. Backend : `SettlementService.getSettlementsForEdition`/`getAssociationRetainedTotal` (calcul batché, pas de N+1), `EditionSummaryReportDto` +2 champs, `ReportService.getEditionReport` étendu, nouveau `ReportExportService` (CSV RFC 4180 + BOM UTF-8, réutilise `EditionReportNotAllowedException`), 2 endpoints d'export sur `AdminReportController`. Frontend : modèle/service/composant étendus (2 stat-tiles, bloc export avec téléchargement Blob), i18n FR/EN. Écart non anticipé détecté et corrigé pendant l'écriture des tests : `ItemMapper.toCatalogDto` mappe `barcode` sur le format avec tiret (`getFormattedBarcode()`), pas le format brut — correction du test uniquement, aucun changement de code de production. Nouveau test `ReportExportIT` (storyboard à deux vendeurs soldés différemment). 450/450 tests backend (440 + 10 nouveaux), 598/598 tests frontend (586 + 12 nouveaux), build de production frontend sans erreur, aucune régression. Vérification visuelle humaine de `/admin/reports` en attente (dev-story non supervisé). Statut → review.
