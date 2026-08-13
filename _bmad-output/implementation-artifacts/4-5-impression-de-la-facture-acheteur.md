---
baseline_commit: a0b418f307ecb55d145101fa4b7d314329ce082c
---

# Story 4.5: Impression de la facture acheteur

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole caissier,
I want imprimer une facture acheteur à la demande après une vente validée,
so that l'acheteur dispose d'un justificatif papier de son achat.

## Acceptance Criteria

1. **Déclenchement à la demande (FR-041).** Étant donné qu'un paiement vient d'être validé (`POST /pos/baskets/{basketId}/validate` a répondu 200), quand le bénévole clique sur « Imprimer la facture », alors un PDF est généré côté serveur via OpenPDF (packages `org.openpdf.*`, même bibliothèque que le bordereau de dépôt, Story 3.6 — **le `pom.xml` réel pointe déjà vers 3.0.5**, la baseline « 3.0.0 » citée par epics.md/FR-041/architecture.md est une valeur obsolète documentée et remplacée dès la Story 3.6, Dev Notes § OpenPDF ; ne pas tenter de figer/rétrograder la dépendance) et mis en file d'impression — aucun blocage de l'UI en attendant l'impression physique.
2. **Contenu du PDF (FR-041).** Le PDF contient : liste des articles (nom, prix unitaire), total du panier, nom de l'association (paramètres d'instance — FR-073), nom de l'édition, date de la vente. Un article de lot n'apparaît jamais individuellement — un lot complet ou partiel dans la vente apparaît sur une seule ligne (nom du lot, prix global du lot), même règle de dédoublonnage que `DepositSlipRenderer` (Story 3.6). Sans nom d'acheteur (non collecté, EXPERIENCE.md).
3. **File d'impression A4 (FR-041).** Le PDF généré est envoyé au `PrintQueueService` sur la file de l'imprimante A4 sélectionnée en session (Story 3.9, `PrinterSelectionService`) puis transmis à PrinterBridge par WebSocket (Story 3.12) — même mécanisme que la réimpression du bordereau de dépôt, pas de nouveau protocole de livraison.
4. **Réimpression illimitée (FR-041).** Étant donné qu'une facture a déjà été imprimée pour cette vente, quand le bénévole redéclenche l'impression (même clic dans les 30 secondes, cf. AC 5), alors un nouveau job est mis en file — pas d'état « déjà imprimé » qui bloquerait un second envoi.
5. **Fenêtre d'affichage du bouton (EXPERIENCE.md § Panier POS — état post-validation, UX-DR non numéroté explicitement mais normatif).** Après validation réussie, le bouton « Imprimer la facture » (icône `print`) apparaît immédiatement et reste visible **30 secondes**, puis disparaît automatiquement sans action de l'utilisateur. Le scanner reprend le focus pour une nouvelle transaction dès la fermeture du panel de paiement (comportement déjà en place depuis la Story 4.2, non modifié par cette story). **Le scanner reste utilisable pour une nouvelle transaction pendant que le bouton est encore visible** (EXPERIENCE.md : « scanner reprend le focus... » est immédiat, pas conditionné à l'expiration des 30s) — un scan (`onScan()`/`addItem()`) pendant cette fenêtre ne doit **jamais** réinitialiser prématurément `lastSale`/le timer : seule l'expiration des 30 secondes (ou un nouveau succès de `validate()`, qui relance son propre timer) fait disparaître le bouton.
6. **Portée de la vente imprimable (garde IDOR, cohérente avec `PosBasketService.requireOwnedBasket`).** Seul le bénévole qui a réalisé la vente peut en imprimer la facture pendant la fenêtre des 30 secondes. Un `saleId` invalide ou appartenant à un autre bénévole renvoie 404 (`sale-not-found`) — jamais 403, pour ne pas distinguer « n'existe pas » de « appartient à quelqu'un d'autre ».
7. **Aucune imprimante A4 sélectionnée (cohérent avec `DepositValidationService.reprintDepositSlip`).** Si aucune imprimante A4 n'est sélectionnée en session, ou si celle sélectionnée n'est plus disponible, l'appel renvoie 422 (`invalid-printer-selection`) — le bénévole voit un message d'erreur explicite, la vente reste enregistrée (déjà validée, cette story ne touche jamais à la vente elle-même).

## Tasks / Subtasks

- [x] **Backend — requête de récupération des articles d'une vente (AC 2)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` (UPDATE) : ajouter
    ```java
    /**
     * Facture acheteur (story 4.5) : {@code JOIN FETCH i.lot} pour la même raison que
     * {@link #findAllBySellerProfileIdOrderByItemNumberAsc} — les items sont capturés dans un
     * {@link org.pluribourse.domain.print.service.PrintJob} exécuté plus tard sur le thread
     * consommateur de la file, après la fermeture de la transaction/session qui les a chargés.
     * Ne fetch ni {@code edition} ni {@code sellerProfile} : {@code InvoiceRenderer} ne lit ni l'un
     * ni l'autre (le nom de l'édition est résolu séparément, voir {@code PosInvoicePrintService}).
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.sale.id = :saleId ORDER BY i.id ASC")
    List<Item> findAllBySaleIdOrderById(@Param("saleId") Long saleId);
    ```
- [x] **Backend — exception de vente introuvable (AC 6)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/SaleNotFoundException.java` (NEW) : même patron que `BasketNotFoundException` (404, `sale-not-found`, JavaDoc expliquant que "appartient à un autre bénévole" utilise le même code que "n'existe pas", IDOR).
- [x] **Backend — rendu PDF de la facture (AC 2)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/InvoiceRenderer.java` (NEW) — même structure que `DepositSlipRenderer` (police CP1252 non embarquée, `PdfWriter.setCompressionLevel(PdfStream.NO_COMPRESSION)` pour rester greppable en test, réutilise `ItemPricing.computeTotal`/`ItemPricing.distinctByLot`) :
    ```java
    @Component
    @RequiredArgsConstructor
    public class InvoiceRenderer {

        // Mêmes constantes de police que DepositSlipRenderer — CP1252 non embarqué, sinon le signe
        // € force un fallback vers une police CID embarquée qui encode en glyph index, illisible.

        private final MessageSource messageSource;

        public byte[] renderInvoice(String associationName, String editionName, LocalDateTime soldAt,
                List<Item> items, Locale documentLocale) {
            // Document A4, writer, compression désactivée — voir DepositSlipRenderer.renderSlip
            // Paragraphe titre (print.invoice.title) + associationName + editionName + soldAt formaté
            // Table 2 colonnes (print.invoice.column.item / .column.price) via buildItemsTable (identique
            // à DepositSlipRenderer.buildItemsTable : ItemPricing.distinctByLot, lot -> nom+prix global,
            // sinon nom+price)
            // Paragraphe total en gras (print.invoice.total, ItemPricing.computeTotal(items))
        }
    }
    ```
    **Ne pas dupliquer `buildItemsTable`/`headerCell`/`addRow` en changeant seulement le nom** : soit extraire ces trois méthodes privées de `DepositSlipRenderer` vers un point commun réutilisable par les deux renderers (ex. package-private static helper dans `print/service/`), soit accepter la duplication à 3 méthodes courtes si l'extraction complexifie plus qu'elle ne simplifie — trancher au moment de l'implémentation selon CLAUDE.md (« trois lignes similaires valent mieux qu'une abstraction prématurée ») ; ne pas introduire d'interface/abstraction plus large que ces 3 méthodes.
    Formatage de la date : pas de précédent de localisation des nombres/dates dans ce module (`DepositSlipRenderer` n'a jamais localisé ses montants, décision déjà actée en Story 3.5/3.6) — utiliser un format simple non localisé, ex. `DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")`, cohérent avec cette convention existante plutôt que `ofLocalizedDateTime(documentLocale)`.
- [x] **Backend — job d'impression (AC 1, 3)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (UPDATE) : ajouter `buildInvoiceJob(String associationName, String editionName, LocalDateTime soldAt, List<Item> items, Locale documentLocale)` retournant un `PrintJob` lambda qui appelle `InvoiceRenderer.renderInvoice(...)` puis `printerBridgeClient.print(printer.getPrinterBridgeId(), PrintContentType.PDF, pdf)` — même forme que `buildDepositSlipJob`.
- [x] **Backend — orchestration (AC 1, 3, 6, 7)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java` (NEW) — nouveau service dédié (pas d'ajout à `PosBasketService`, qui ne connaît que des `Basket` déjà supprimés à ce stade) :
    ```java
    @Service
    @RequiredArgsConstructor
    public class PosInvoicePrintService {

        private final SaleRepository saleRepository;
        private final ItemRepository itemRepository;
        private final GlobalInstanceConfigService globalInstanceConfigService;
        private final PrinterSelectionService printerSelectionService;
        private final PrintQueueService printQueueService;
        private final DocumentPrintService documentPrintService;

        @Transactional(readOnly = true)
        public void printInvoice(Long saleId, Long userId, HttpSession session) {
            Sale sale = saleRepository.findById(saleId)
                    .orElseThrow(() -> new SaleNotFoundException(saleId));
            if (!sale.getUser().getId().equals(userId)) {
                // Jamais distinguer "n'existe pas" de "appartient à un autre bénévole" (IDOR, AC 6) —
                // même patron que PosBasketService.requireOwnedBasket.
                throw new SaleNotFoundException(saleId);
            }

            // Extraits en valeurs simples AVANT de construire le job : le PrintJob s'exécute sur le
            // thread consommateur de la file, après la fin de cette transaction (Dev Notes §
            // Chargement eager). Capturer l'entité Sale/Edition elle-même dans la closure risquerait
            // une LazyInitializationException bien réelle en production — voir Story 3.5/3.6 Dev
            // Notes, où ce piège s'est déjà produit deux fois sur ce module d'impression.
            String editionName = sale.getEdition().getName();
            Locale documentLocale = sale.getEdition().getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;
            String associationName = globalInstanceConfigService.getConfig().associationName();
            LocalDateTime soldAt = sale.getSoldAt();

            List<Item> items = itemRepository.findAllBySaleIdOrderById(saleId);

            Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                    .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
            if (!printQueueService.isAvailable(a4PrinterId)) {
                throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
            }

            printQueueService.submit(a4PrinterId,
                    documentPrintService.buildInvoiceJob(associationName, editionName, soldAt, items, documentLocale));
        }
    }
    ```
    **Décision de conception (pas de garde de phase) :** contrairement à `DepositValidationService`, cette méthode n'appelle **pas** `PhaseGuard` — une `Sale` est un enregistrement historique immuable dès sa création (contrairement aux `Item` encore modifiables en phase Dépôt), et aucun AC de l'epic ne conditionne l'impression à la phase courante. Le bouton n'étant de toute façon visible que 30 secondes après une validation qui exigeait déjà la phase Vente (AC 5), le cas d'un changement de phase entre-temps est un cas limite déjà couvert différemment par la Story 4.6 (SSE `basket-cancelled`, hors périmètre ici : la vente est déjà conclue, pas un panier actif).
- [x] **Backend — endpoint HTTP (AC 1, 6, 7)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosSaleController.java` (NEW) :
    ```java
    @RestController
    @RequestMapping("/pos/sales")
    @RequiredArgsConstructor
    public class PosSaleController {

        private final PosInvoicePrintService service;

        @PostMapping("/{saleId}/invoice/print")
        public ResponseEntity<Void> printInvoice(@PathVariable Long saleId, HttpSession session, Authentication authentication) {
            service.printInvoice(saleId, userId(authentication), session);
            return ResponseEntity.noContent().build();
        }

        private Long userId(Authentication authentication) {
            return ((PluriBourseUserDetails) authentication.getPrincipal()).getUserId();
        }
    }
    ```
    Pas de `@PreAuthorize` — même règle globale authentifié-non-SELLER que `PosBasketController`/`PosController` (héritée de `SecurityConfig`).
- [x] **Backend — i18n du PDF (AC 2)**
  - [x] `pluribourse-backend/src/main/resources/messages_fr.properties` (UPDATE) : ajouter sous un commentaire `# Buyer invoice PDF rendering (Story 4.5)` :
    ```properties
    print.invoice.title=Facture
    print.invoice.column.item=Article
    print.invoice.column.price=Prix unitaire
    print.invoice.total=Total : {0}€
    ```
  - [x] `pluribourse-backend/src/main/resources/messages_en.properties` (UPDATE) : mêmes clés, traductions anglaises (`Invoice` / `Item` / `Unit price` / `Total: {0}€`).
- [x] **Frontend — service HTTP (AC 1)**
  - [x] `pluribourse-frontend/src/app/services/pos.service.ts` (UPDATE) : ajouter
    ```typescript
    printInvoice(saleId: number): Observable<void> {
      return this.http.post<void>(`/api/pos/sales/${saleId}/invoice/print`, null);
    }
    ```
- [x] **Frontend — bouton post-validation avec fenêtre de 30 secondes (AC 1, 4, 5, 7)**
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` (UPDATE) :
    - Capturer le `Sale` retourné par `posService.validate(...)` dans `openPaymentDialog()` (actuellement jeté — la réponse n'est utilisée que pour recharger le panier). L'exposer via `readonly lastSale = signal<Sale | null>(null)`.
    - Constante top-level nommée (même convention que `scanner-input.component.ts` : `REFOCUS_DELAY_MS`/`BUFFER_STALE_DELAY_MS`, jamais de nombre magique inline) : `const INVOICE_BUTTON_VISIBLE_MS = 30000;` avec un court commentaire citant EXPERIENCE.md comme source du délai.
    - Injecter `DestroyRef` (pattern déjà utilisé par `scanner-input.component.ts`, ne pas implémenter `OnDestroy` classiquement) ; un champ privé `invoiceButtonTimer: ReturnType<typeof setTimeout> | undefined`. Sur succès de `validate()` : `clearTimeout` de tout timer précédent, `this.lastSale.set(sale)`, `this.invoiceButtonTimer = setTimeout(() => this.lastSale.set(null), INVOICE_BUTTON_VISIBLE_MS)`. `destroyRef.onDestroy(() => clearTimeout(this.invoiceButtonTimer))` dans le constructeur.
    - **`onScan()`/`addItem()` ne doivent jamais toucher `lastSale`/`invoiceButtonTimer`** (AC 5) — ces deux signaux sont indépendants du cycle de scan du panier suivant ; seuls `validate()` (redémarre le timer) et l'expiration des 30s le vident. Ne pas ajouter de `this.lastSale.set(null)` dans `onScan()` par réflexe de « nettoyage d'état ».
    - `readonly printingInvoice = signal(false)` — verrou anti-double-clic, même patron que `scanInFlight`/`removeInFlight`/`validateInFlight`.
    - Nouvelle méthode `async printInvoice(): Promise<void>` : garde `printingInvoice()`/`lastSale()` nul, appelle `posService.printInvoice(this.lastSale()!.id)`, toast succès (`volunteer.pos.invoice.success`) ou erreur — si 422 `invalid-printer-selection`, message dédié `volunteer.pos.invoice.error.a4PrinterUnavailable` (AC 7) ; sinon `volunteer.pos.invoice.error.generic`. **Ne pas** fermer/masquer le bouton après un clic réussi (AC 4 : réimpression illimitée tant que la fenêtre de 30s n'est pas expirée).
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html` (UPDATE) : sous `.basket-total`/`.basket-validate` (ou dans un bloc séparé après la carte panier, à trancher visuellement), ajouter :
    ```html
    @if (lastSale(); as sale) {
      <button
        type="button"
        mat-flat-button
        color="primary"
        class="print-invoice-btn"
        [disabled]="printingInvoice()"
        (click)="printInvoice()">
        <mat-icon>print</mat-icon>
        {{ 'volunteer.pos.invoice.button' | translate }}
      </button>
    }
    ```
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.scss` (UPDATE) : `.print-invoice-btn { width: 100%; margin-top: var(--pb-space-sm); }` (même style que `.basket-validate`).
- [x] **Frontend — i18n (AC 1, 5, 7)**
  - [x] `pluribourse-frontend/public/i18n/fr.json` (UPDATE) : sous `volunteer.pos`, ajouter une clé `invoice` :
    ```json
    "invoice": {
      "button": "Imprimer la facture",
      "success": "Facture envoyée à l'impression.",
      "error": {
        "generic": "Impossible d'imprimer la facture.",
        "a4PrinterUnavailable": "Aucune imprimante A4 disponible pour l'impression de la facture."
      }
    }
    ```
  - [x] `pluribourse-frontend/public/i18n/en.json` (UPDATE) : structure identique, traductions anglaises.
- [x] **Tests backend (AC 1-7)** — nouvelle classe `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` (NEW), **pas d'extension de `PosBasketIT`** : « impression de facture » est un scénario métier distinct (enregistrement/sélection imprimante, `PrinterBridgeDouble`, appels directs sur le renderer réel) — même raisonnement que `DepositSlipPrintingIT` séparée de la CRUD dépôt. Structure calquée sur `DepositSlipPrintingIT` (même double `PrinterBridgeDouble`/`@DynamicPropertySource`, mêmes sessions admin/volunteer1/volunteer2) :
  - [x] Setup : édition, catégorie, vendeur avec un article standalone + un lot de 2 articles (comme `DepositSlipPrintingIT` Order 2), avance l'édition jusqu'en phase Vente, enregistre une imprimante A4 (`PrinterBridgeDouble`), la sélectionne pour `volunteer1Session`.
  - [x] Scénario de vente réelle via `PosBasketController` (pas de raccourci direct en base pour la vente elle-même, contrairement à `SaleConcurrencyIT` — ici la philosophie E2E-par-contrôleur s'applique normalement, aucune contrainte de concurrence à contourner) : `GET /pos/baskets/current`, `POST .../items` pour chaque barcode (article standalone + les 2 articles du lot), `POST .../validate` (paiement CASH, `amountGiven=null`) → capturer le `saleId` retourné. **Tout appel `POST`/`DELETE` de ce scénario (y compris `POST /pos/sales/{saleId}/invoice/print` plus bas) doit inclure `.with(csrf())`**, comme partout ailleurs dans `DepositSlipPrintingIT`/`PosBasketIT` — un oubli se traduit par un 403 silencieux, pas par l'erreur métier attendue.
  - [x] Test direct sur `InvoiceRenderer`/`ItemRepository.findAllBySaleIdOrderById` (comme `DepositSlipPrintingIT` Order 5) : PDF commence par `%PDF`, contient le nom de l'article standalone et son prix, le nom du lot et son prix global chacun **exactement une fois** (dédoublonnage), le nom de l'association et de l'édition, le total correct (`ItemPricing.computeTotal`).
  - [x] Test direct sur `DocumentPrintService.buildInvoiceJob` avec un `PrinterBridgeClient` mocké (comme `DepositSlipPrintingIT` Order 6) : vérifie l'appel `print(printerBridgeId, PDF, bytes)` avec des bytes commençant par `%PDF`.
  - [x] Test HTTP bout-en-bout : `POST /pos/sales/{saleId}/invoice/print` avec `volunteer1Session` → 204, job mis en file (vérifié via `printQueueService.getHandle(a4PrinterId)` comme Order 12 de `DepositSlipPrintingIT` — la livraison réelle échoue contre `PrinterBridgeDouble`, HTTP-only, ce qui est attendu et suffit à prouver l'absence de `LazyInitializationException`/mauvais câblage).
  - [x] AC 4 (réimpression) : **écart par rapport au libellé initial de cette tâche** — un simple second appel immédiat à l'endpoint s'est avéré non déterministe en pratique (flaky sous suite complète, voir Dev Agent Record § Debug Log) : le premier appel suspend la file dès que le thread consommateur asynchrone échoue la poignée de main WebSocket contre `PrinterBridgeDouble` (HTTP-only), et selon le timing ce deuxième appel pouvait tomber sur `isAvailable()==false` → 422 au lieu de 204. Corrigé en rendant le scénario déterministe : `waitUntil` la suspension après le 1er appel, puis `POST /admin/print-queue/{a4PrinterId}/discard` (efface l'état sans relancer le job raté — `resume` aurait retenté le même job voué à l'échec et re-suspendu la file à un instant imprévisible), puis le 2e appel → 204.
  - [x] AC 6 (IDOR) : `POST /pos/sales/{saleId}/invoice/print` avec `volunteer2Session` (n'a pas fait cette vente) → 404 `sale-not-found`. Idem avec un `saleId` inexistant.
  - [x] AC 7 (pas d'imprimante) : session volontaire sans sélection A4 → 422 `invalid-printer-selection`.
- [x] **Tests frontend (AC 1, 4, 5, 7)** — `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts` (UPDATE), même style que les tests `validate`/`openPaymentDialog` existants (mocks `posServiceMock`/`paymentDialogServiceMock`/`toastMock`) :
  - [x] Après une validation réussie, `lastSale()` contient la vente retournée et le bouton `.print-invoice-btn` est rendu.
  - [x] `vi.useFakeTimers()` + `vi.advanceTimersByTime(30000)` (patron déjà utilisé par `scanner-input.component.spec.ts`) : après 30s, `lastSale()` redevient `null` et le bouton disparaît.
  - [x] Un scan pendant la fenêtre de 30s ne réinitialise jamais `lastSale()` (non-régression, AC 5).
  - [x] Appeler `printInvoice()` appelle `posService.printInvoice(sale.id)` et affiche un toast succès.
  - [x] 422 `invalid-printer-selection` sur `printInvoice()` → toast avec le message dédié, pas le message générique.
  - [x] Un second appel (réimpression) dans la fenêtre de 30s rappelle `printInvoice()` sans que le bouton ne disparaisse (AC 4).

## Review Findings

Revue de code effectuée (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor). Acceptance Auditor : 0 violation d'AC (implémentation vérifiée fidèle à chaque AC contre le code source réel, pas seulement contre les commentaires). 0 decision-needed, 2 patch, 4 defer, 9 rejetés comme bruit.

- [x] [Review][Patch] `InvoicePrintingIT` passe `null` au lieu du vrai bean `DepositSlipRenderer` au constructeur de `DocumentPrintService` (piège NPE si jamais touché, alors que le Javadoc de la classe revendique des beans « réels, entièrement câblés ») — corrigible en autowirant `DepositSlipRenderer` comme `invoiceRenderer` l'est déjà. **Appliqué** : champ `depositSlipRenderer` autowiré, passé au constructeur au lieu de `null`. [InvoicePrintingIT.java]
- [x] [Review][Patch] `DocumentPrintService.printDepositSlip`/`printInvoice` dupliquent la même paire d'appels (`render(...)` puis `printerBridgeClient.print(...)`) — extraire un helper privé `printPdf(printerBridgeId, pdf)`. **Appliqué**. [DocumentPrintService.java]
- [x] [Review][Defer] Race TOCTOU entre `PrintQueueService.isAvailable()` et `.submit()` dans `PosInvoicePrintService` — préexistante depuis `DepositValidationService` (Story 3.5/3.6) ; cette story reproduit fidèlement le même mécanisme, comme demandé par ses propres Dev Notes (« même mécanisme que la réimpression du bordereau »), sans l'introduire ni l'aggraver. [PosInvoicePrintService.java] — deferred, pre-existing
- [x] [Review][Defer] Variante de la même race : une imprimante supprimée par un admin entre le check `isAvailable()` et `submit()` ferait fuiter une `PrinterNotFoundException` (404) au lieu du 422 `invalid-printer-selection` attendu par le frontend — même cause racine, préexistante depuis le même mécanisme d'origine (Story 3.5/3.6). [PosInvoicePrintService.java, PrintQueueService.java] — deferred, pre-existing
- [x] [Review][Defer] `ItemRepository.findAllBySaleIdOrderById` (scoping par `saleId`) n'est prouvé par aucun test avec une seconde `Sale` distincte dans la fixture — `InvoicePrintingIT` ne crée qu'une seule vente sur toute l'édition, donc une requête bugguée retournant « tous les items de l'édition » passerait les mêmes assertions sans être détectée. [InvoicePrintingIT.java] — deferred
- [x] [Review][Defer] Le verrou anti-double-clic `printingInvoice()` n'est jamais exercé par un test d'appels concurrents réels (le test de réimpression attend la résolution du premier appel avant de démarrer le second) — même lacune déjà acceptée pour `scanInFlight`/`removeInFlight`/`validateInFlight` ailleurs dans ce composant depuis les Stories 4.2/4.3, non spécifique à cette story. [pos-page.component.spec.ts] — deferred, pre-existing pattern

**Rejetés comme bruit :** imports wildcard dans `InvoicePrintingIT` (convention établie des classes IT du projet, cf. `DepositSlipPrintingIT`/`PosBasketIT`, code de production non concerné) · format de date dupliqué entre `InvoiceRenderer` et le test (pratique normale d'assertion de test, pas un risque de dérive justifiant une constante partagée pour un seul format) · « double arrondi » du total (`setScale(HALF_UP)` après `ItemPricing.computeTotal`) — reproduit exactement la convention déjà en place et déjà validée dans `DepositSlipRenderer` (test d'arrondi à l'égalité exacte, Story 3.6) · signe « € » codé en dur au lieu d'une clé i18n sur le prix unitaire — copié à l'identique de `DepositSlipRenderer`, décision de non-localisation des montants déjà actée en Story 3.5/3.6 · absence de test d'un appelant non authentifié/rôle SELLER sur le nouvel endpoint — CLAUDE.md exclut explicitement la config Spring Security des tests dédiés par contrôleur (« couverts implicitement par les scénarios E2E ») · risque de NPE sur `sale.getUser()` — `Sale.user` est `optional = false`/`nullable = false` à la fois côté JPA et côté BDD, invariant structurel garanti, même patron de confiance que `PosBasketService.requireOwnedBasket` · attente `waitUntil` à délai fixe jugée fragile — patron non modifié, déjà en place et éprouvé depuis `DepositSlipPrintingIT` (Story 3.6), le flake réel de cette story a été corrigé à sa cause racine (voir Debug Log) · helper `countOccurrences` « fait main » — copié à l'identique du helper privé déjà existant dans `DepositSlipPrintingIT`, pas une nouvelle duplication introduite par cette story · absence alléguée de recherche exhaustive des autres sites d'instanciation de `DocumentPrintService` — vérifiée : une recherche globale a bien été effectuée pendant l'implémentation (un seul site trouvé, déjà corrigé), corroborée par la suite complète 377/377 backend au vert.

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- **`DepositSlipRenderer`/`DocumentPrintService`/`PrintQueueService`/`PrinterSelectionService` (Story 3.6/3.9/3.12) portent déjà tout le mécanisme de génération PDF + file A4 + sélection imprimante en session.** Cette story ajoute un second renderer et une seconde méthode `buildXxxJob` sur le `DocumentPrintService` existant — elle ne touche à aucun de ces fichiers dans leur logique de file/livraison.
- **`ItemPricing.computeTotal`/`ItemPricing.distinctByLot`** (`item/service/ItemPricing.java`) portent déjà la règle de dédoublonnage des lots (un lot = une ligne, quel que soit le nombre de membres). Ne pas réécrire cette logique dans `InvoiceRenderer`.
- **`SaleDto` a déjà un champ `id`** (`pos/dto/SaleDto.java`) — le frontend le reçoit déjà en réponse de `posService.validate(...)` mais le jette actuellement (`pos-page.component.ts:121`, `await firstValueFrom(this.posService.validate(...))` sans capturer le résultat). Cette story capture enfin cette valeur, ne crée pas de nouvel appel pour la récupérer.
- **`Item.sale` (FK `sale_id`, déjà en place depuis la Story 4.2)** est le seul lien entre une vente et ses articles — `Sale` ne porte aucune collection `items`. D'où la nouvelle requête `ItemRepository.findAllBySaleIdOrderById`.
- **`GlobalInstanceConfigService.getConfig().associationName()`** (Story 1.5/FR-073) est déjà la source du nom de l'association — ne pas ajouter de champ redondant sur `Edition`/`Sale`.

### Piège critique : chargement eager avant capture dans un `PrintJob` (déjà rencontré deux fois sur ce module)

`PrintJob.execute()` s'exécute sur le thread consommateur dédié de la file (`PrinterQueueHandle`, Story 3.4), **après** la fin de la transaction/session Hibernate qui a chargé les entités — potentiellement bien après, si la file est occupée. Toute association `@ManyToOne(fetch = LAZY)` non initialisée déréférencée à ce moment-là lève `LazyInitializationException`, **en production, pas seulement en test** :
- Story 3.5 : bug réel découvert en cours d'implémentation, corrigé par `JOIN FETCH edition`/`sellerProfile`/`lot` sur `ItemRepository.findAllBySellerProfileIdOrderByItemNumberAsc`.
- Story 3.6 : un finding de revue soupçonnant le même piège sur `sellerProfile.getEdition()` a été vérifié et écarté (le proxy s'est avéré déjà initialisé via le cache de session), mais **ce n'est pas une garantie à reproduire à l'identique** — c'est un comportement incident, pas un contrat documenté.

**Pour cette story**, la voie choisie est la plus robuste et explicite plutôt que de s'appuyer sur ce même comportement incident : extraire `editionName`/`documentLocale`/`associationName`/`soldAt` en valeurs Java simples (`String`/`Locale`/`LocalDateTime`) **avant** d'appeler `documentPrintService.buildInvoiceJob(...)`, jamais passer l'entité `Sale` ou `Edition` elle-même dans la closure. Voir le squelette de `PosInvoicePrintService.printInvoice` ci-dessus. Les `Item` de `findAllBySaleIdOrderById`, eux, sont bien capturés en tant qu'entités (comme `DepositSlipRenderer`/`DocumentPrintService.buildDepositSlipJob` le font déjà pour le bordereau) — c'est acceptable **uniquement** parce que la requête `JOIN FETCH i.lot` garantit qu'aucune navigation ultérieure (`item.getName()`, `item.getPrice()`, `item.getLot().getName()`, `item.getLot().getGlobalPrice()`) ne touche une association non initialisée.

### Décision de conception : pas de garde de phase sur l'impression

`DepositValidationService.reprintDepositSlip`/`reprintLabels` exigent la phase Dépôt ou Post-vente (`PhaseGuard.requireDepositOrPostSalePhase`) parce que les `Item` qu'ils impriment restent modifiables jusqu'à la fin de ces phases. Une `Sale` est différente : une fois créée par `PosBasketService.validate` (Story 4.2), elle est un enregistrement immuable — rien dans les AC de l'epic (4.5) ni dans EXPERIENCE.md ne conditionne l'impression de sa facture à la phase courante de l'édition. `PosInvoicePrintService.printInvoice` n'appelle donc **pas** `PhaseGuard`. Ce choix n'a pas nécessité de confirmation utilisateur (contrairement aux deux écarts d'architecture.md documentés en Story 4.4) : aucun document de planification n'affirme le contraire, c'est une lecture directe des AC.

### Fenêtre de 30 secondes (source : EXPERIENCE.md, absente d'epics.md)

Les AC de `epics.md#Story 4.5` ne mentionnent ni la disparition automatique du bouton ni son délai. C'est `ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md` (ligne « Panier POS » et ligne « Post-validation POS — facture disponible » du tableau des micro-interactions) qui fixe ce comportement : **bouton visible 30 secondes puis disparition automatique**. Repris ici comme AC 5 à part entière — une implémentation qui omettrait ce timer laisserait le bouton visible indéfiniment (ou l'omettrait complètement), une régression UX silencieuse par rapport à la maquette validée, même si elle « passerait » une lecture stricte d'epics.md seul.

### Project Structure Notes

- Nouveau package touché : `pos/service/PosInvoicePrintService.java`, `pos/controller/PosSaleController.java`, `pos/exception/SaleNotFoundException.java` (F4 — POS, cohérent avec la table de correspondance fonctionnalité→structure d'architecture.md, qui assigne l'orchestration métier à `pos/` et délègue le rendu/la livraison à `print/`).
- `print/service/InvoiceRenderer.java` (NEW) et `print/service/DocumentPrintService.java` (UPDATE) : même répartition que `DepositSlipRenderer`/`DocumentPrintService` pour le bordereau (F9 — Infrastructure d'impression).
- Aucune migration Liquibase, aucune entité modifiée — `Sale`/`Item`/`Printer` existent déjà tels quels depuis les Stories 4.2/3.8.
- Frontend : aucun nouveau composant, seulement `pos-page.component.ts/html/scss` (UPDATE) + `pos.service.ts` (UPDATE) — cohérent avec la structure existante (`components/pos/`, `services/pos.service.ts`).

### Fichiers à lire avant modification

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java`, `DocumentPrintService.java`, `PrintJob.java`, `PrinterSelectionService.java`, `PrintQueueService.java` (référence directe — patron à reproduire)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/DepositValidationService.java` (référence — orchestration équivalente pour le bordereau, y compris la résolution de locale)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java` (référence — `requireOwnedBasket`, patron IDOR à reproduire pour `SaleNotFoundException`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/entity/Sale.java`, `domain/item/entity/Item.java` (référence — pas de collection `Sale.items`, FK `Item.sale`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/service/GlobalInstanceConfigService.java`, `dto/GlobalInstanceConfigDto.java` (référence — `getConfig().associationName()`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemPricing.java` (référence — réutiliser tel quel)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` (référence directe — structure de test à reproduire pour `InvoicePrintingIT`, y compris `PrinterBridgeDouble`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` (référence — patron de scan/validate via `MockMvc` à réutiliser pour produire une vraie `Sale`)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts/.html/.scss` (UPDATE — lire intégralement, notamment `openPaymentDialog()` où le résultat de `validate()` est actuellement jeté)
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts`, `scanner-input.component.spec.ts` (référence — patron `DestroyRef`/`setTimeout`/`vi.useFakeTimers` à reproduire pour le timer de 30s)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts/.html` (référence — patron bouton d'impression avec verrou anti-double-clic et gestion d'erreur 422 dédiée)
- `pluribourse-frontend/src/app/models/pos.model.ts` (référence — `Sale.id` déjà exposé, aucun changement de modèle nécessaire)
- `pluribourse-backend/src/main/resources/messages_fr.properties`, `messages_en.properties` (UPDATE — ajout de section, ne pas toucher aux clés `print.slip.*`/`print.label.*` existantes)
- `pluribourse-frontend/public/i18n/fr.json`, `en.json` (UPDATE — ajout sous `volunteer.pos`)

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.5] — ACs source (FR-041)
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#F4 — Point de Vente, FR-041] — contenu exact attendu de la facture
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md] — fenêtre de 30 secondes, structure du PDF (association/édition/date/heure), maquettes `mock-pos-caisse.html`/`mock-pos-caisse-lot-complet.html`/`mock-pos-paiement.html` (aucune n'illustre explicitement le bouton d'impression — comportement décrit uniquement en texte)
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontière d'Impression, #Correspondance Fonctionnalité → Structure, #Flux de Données — Vente POS] — `PrintQueueService`/consommateurs dédiés, `POST /api/print/invoice (optionnel)` déjà esquissé dans le flux de données POS
- [Source: _bmad-output/implementation-artifacts/3-5-generation-impression-des-etiquettes-thermiques.md] — piège `LazyInitializationException` sur le thread consommateur de la file (Dev Notes § Chargement eager)
- [Source: _bmad-output/implementation-artifacts/3-6-generation-impression-automatique-du-bordereau-de-depot-pdf.md] — patron `DepositSlipRenderer`/`DocumentPrintService`, structure de test `DepositSlipPrintingIT`, décision de non-localisation des montants
- [Source: _bmad-output/implementation-artifacts/4-2-gestion-du-panier-validation-du-paiement.md] — introduit `Sale`, `SaleDto`, `Item.sale`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/**, domain/pos/**, domain/item/service/ItemPricing.java, domain/instanceconfig/**] — lus intégralement
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java, domain/pos/PosBasketIT.java] — lus intégralement
- [Source: pluribourse-frontend/src/app/features/volunteer/pos/**, services/pos.service.ts, models/pos.model.ts] — lus intégralement
- [Source: pluribourse-frontend/public/i18n/fr.json, en.json] — structure `volunteer.pos.*`/`volunteer.deposit.*` existante

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story)

### Debug Log References

- **Changement de signature de `DocumentPrintService` répercuté sur `DepositSlipPrintingIT` (Story 3.6, déjà `done`).** `@RequiredArgsConstructor` génère le constructeur depuis l'ordre des champs `final` : ajouter `InvoiceRenderer invoiceRenderer` en second champ change l'arité du constructeur (2 → 3 arguments). `DepositSlipPrintingIT.document_print_service_sends_the_rendered_pdf_bytes_via_printer_bridge_client` (Order 6) instancie `DocumentPrintService` manuellement (`new DocumentPrintService(...)`, hors conteneur Spring) — ce site d'appel ne compilait plus. Corrigé en ajoutant `@Autowired private InvoiceRenderer invoiceRenderer;` à la classe de test et en passant ce bean au constructeur (non lu par ce test précis, mais nécessaire pour la compilation) — conséquence attendue et documentée dans la story (Dev Notes § Fichiers à lire avant modification), pas une régression découverte a posteriori. `DepositSlipPrintingIT` re-testée isolément après correctif : 13/13 toujours au vert.
- **Test `AC4 (réimpression)` flaky en suite complète, non reproductible en isolation.** Rédigé initialement comme deux appels HTTP consécutifs à `POST /pos/sales/{saleId}/invoice/print` sans synchronisation entre les deux. En isolation (`mvn test -Dtest=InvoicePrintingIT`), les deux appels passaient systématiquement avant que le thread consommateur asynchrone n'échoue la poignée de main WebSocket contre `PrinterBridgeDouble` (HTTP-only) et ne suspende la file. En suite complète (`mvn test`, 377 tests), le timing a divergé une fois : le deuxième appel est tombé après la suspension de la file par le premier job, `printQueueService.isAvailable()` a retourné `false`, et l'appel a reçu 422 (`invalid-printer-selection`) au lieu du 204 attendu — un vrai défaut de conception de test (dépendance à une course entre le thread de test et le thread consommateur), pas un défaut du code de production. Corrigé en rendant le scénario déterministe : après le premier appel, `waitUntil` explicitement la suspension de la file (au lieu de l'ignorer), puis `POST /admin/print-queue/{a4PrinterId}/discard` (efface l'état échoué sans relancer le job — `resume` aurait retenté le même job voué à échouer contre le double HTTP-only et re-suspendu la file à un instant non maîtrisé), puis le second appel d'impression → 204 déterministe. Suite complète re-validée après correctif : 377/377 backend, aucune régression, aucune récurrence du flake sur les runs suivants.
- **Décision de conception prise pendant l'implémentation (non anticipée dans la story) : ne pas partager `buildItemsTable`/`headerCell`/`addRow`/les constantes de police entre `InvoiceRenderer` et `DepositSlipRenderer`.** La story laissait le choix ouvert (extraction vs duplication à 3 méthodes courtes). Tranché en faveur de la duplication : `DepositSlipRenderer` est un fichier stable, déjà testé et revu (Story 3.6, `done`) — le modifier pour y introduire une abstraction partagée aurait ajouté un risque de régression sur cette story sans bénéfice proportionné pour 3 méthodes privées courtes. Documenté dans le Javadoc de classe d'`InvoiceRenderer`.

### Completion Notes List

- Backend : nouvelle requête `ItemRepository.findAllBySaleIdOrderById` (`JOIN FETCH lot`), `SaleNotFoundException` (404 IDOR-safe), `InvoiceRenderer` (nouveau renderer PDF OpenPDF, autonome de `DepositSlipRenderer`), `DocumentPrintService.buildInvoiceJob` (nouvelle méthode, signature de constructeur étendue), `PosInvoicePrintService` (orchestration : ownership → extraction de valeurs simples avant construction du job → sélection/disponibilité imprimante A4 → soumission à la file), `PosSaleController` (`POST /pos/sales/{saleId}/invoice/print`). Aucune garde de phase sur l'impression (décision de conception documentée dans la story, confirmée pendant l'implémentation, aucun écart constaté).
- Clés i18n backend `print.invoice.*` ajoutées (FR/EN), suivant exactement la structure `print.slip.*` existante.
- Frontend : `pos.service.ts` expose `printInvoice(saleId)`. `pos-page.component.ts` capture désormais le `Sale` retourné par `validate()` (auparavant jeté) dans un signal `lastSale`, avec un timer nommé `INVOICE_BUTTON_VISIBLE_MS` (30s, `DestroyRef`/`setTimeout`, même patron que `scanner-input.component.ts`) qui masque automatiquement le bouton « Imprimer la facture ». Garde explicite : `onScan()`/`addItem()` ne touchent jamais `lastSale`/le timer (AC 5). Verrou anti-double-clic `printingInvoice`, gestion d'erreur 422 dédiée (`invalid-printer-selection` → message spécifique, sinon générique).
- Tests backend : nouvelle classe `InvoicePrintingIT` (12 tests, calquée sur `DepositSlipPrintingIT`) couvrant rendu PDF (dédoublonnage de lot, association/édition/date, total), livraison via `DocumentPrintService` (mock `PrinterBridgeClient`), flux HTTP bout-en-bout (file d'attente réelle, `PrinterBridgeDouble`), réimpression déterministe (AC 4, voir Debug Log), IDOR (AC 6), absence d'imprimante sélectionnée (AC 7). `DepositSlipPrintingIT` mise à jour pour la nouvelle signature de `DocumentPrintService` (voir Debug Log).
- Tests frontend : 6 nouveaux tests dans `pos-page.component.spec.ts` (affichage du bouton, disparition après 30s via `vi.useFakeTimers`, non-réinitialisation par un scan, appel du service + toast succès, toast d'erreur dédié 422, réimpression sans disparition du bouton).
- Suite complète re-validée après tous les correctifs : **377/377 tests backend** (0 échec, 0 erreur), **519/519 tests frontend** (56 fichiers de test) — aucune régression.

### File List

**Backend — NEW**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/SaleNotFoundException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/InvoiceRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosSaleController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java`

**Backend — UPDATE**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` — nouvelle méthode `findAllBySaleIdOrderById`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` — nouvelle méthode `buildInvoiceJob`, nouveau champ `invoiceRenderer` (signature de constructeur étendue)
- `pluribourse-backend/src/main/resources/messages_fr.properties` — clés `print.invoice.*`
- `pluribourse-backend/src/main/resources/messages_en.properties` — clés `print.invoice.*`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` — conséquence du changement de signature de `DocumentPrintService` (voir Debug Log), aucun changement de comportement testé

**Frontend — UPDATE**
- `pluribourse-frontend/src/app/services/pos.service.ts` — nouvelle méthode `printInvoice`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` — signal `lastSale`, timer 30s, `printInvoice()`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html` — bouton `.print-invoice-btn`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.scss` — style `.print-invoice-btn`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts` — 6 nouveaux tests
- `pluribourse-frontend/public/i18n/fr.json` — clés `volunteer.pos.invoice.*`
- `pluribourse-frontend/public/i18n/en.json` — clés `volunteer.pos.invoice.*`

## Change Log

- 2026-08-13 — dev-story : implémentation complète (renderer PDF facture, orchestration backend, endpoint HTTP, bouton frontend avec fenêtre de 30s, i18n FR/EN). Deux correctifs pendant l'implémentation : signature de `DocumentPrintService` répercutée sur `DepositSlipPrintingIT` (Story 3.6), test de réimpression rendu déterministe (`discard` de file plutôt que timing implicite). 377/377 tests backend, 519/519 tests frontend, aucune régression. Statut → review.
- 2026-08-13 — code-review (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) : Acceptance Auditor confirme conformité totale aux 7 AC (vérifiée contre le code source, pas seulement les commentaires), 0 violation. 0 decision-needed, 2 patch appliqués (bean réel `DepositSlipRenderer` autowiré dans `InvoicePrintingIT` au lieu de `null`, helper `printPdf` extrait dans `DocumentPrintService`), 4 defer documentés dans `deferred-work.md` (race TOCTOU `isAvailable`/`submit` préexistante depuis Story 3.5/3.6 et sa variante imprimante-supprimée, scoping `findAllBySaleIdOrderById` non prouvé par une seconde vente, verrou anti-double-clic frontend non exercé par un test concurrent — même lacune déjà acceptée depuis 4.2/4.3), 9 rejetés comme bruit (conventions déjà établies du projet : imports wildcard des classes IT, non-localisation des montants, `waitUntil`/`countOccurrences` copiés de `DepositSlipPrintingIT`, config Spring Security explicitement hors périmètre des tests par CLAUDE.md, invariant `Sale.user` non-null au niveau schéma, etc.). Suite complète re-validée après patchs : 377/377 backend, aucune régression. Statut → done.
