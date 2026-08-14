---
baseline_commit: 0a5e12dc4c36b504abfa57dcb60d1c7307edbf29
---

# Story 4.6: Gestion du changement de phase dans le composant POS — côté client

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole caissier avec un panier actif,
I want être immédiatement notifié si l'administrateur change la phase pendant que je suis en cours de transaction,
so that je ne tente pas de finaliser une vente dans une phase qui n'est plus valide.

**Dépendance : Story 2.8 (done).** Le backend diffuse déjà l'événement SSE `basket-cancelled` (`{editionId, newPhase}`) sur `GET /api/sse/events`, à chaque transition de phase (avance ou retour arrière) qui annule au moins un panier actif — voir `BasketCancelledEventDto`, `EditionService.savePhaseThenSendEvent`. **Cette story est strictement frontend** : aucun changement backend n'est nécessaire ni attendu.

## Acceptance Criteria

1. **Réception de l'événement pendant une transaction active (FR-090, EXPERIENCE.md § Panier POS).** Étant donné que le composant `PosPageComponent` est monté avec un panier actif (chargé au `ngOnInit`, potentiellement vide mais non `null` — `getOrCreateCurrentBasket` en crée toujours un dès le premier accès à `/volunteer/pos`), quand l'événement SSE `basket-cancelled` arrive, alors :
   - un toast **persistant** apparaît avec le message « La phase a changé. Votre panier a été annulé. » (texte exact d'EXPERIENCE.md ; le tag `UX-DR21` cité par epics.md ne correspond à aucune ancre existante dans les docs UX — EXPERIENCE.md fait foi pour le texte, non bloquant) ;
   - le panier affiché est entièrement vidé ;
   - le champ de saisie scanner est désactivé.
2. **Pas de réactivation automatique.** Étant donné que le scanner est désactivé suite à l'annulation, quand le bénévole tente de scanner à nouveau, alors rien ne se produit — seul un rechargement complet de la page réactive le composant (pas de mécanisme de réactivation en JS à implémenter).
3. **Silence si aucun panier actif (cas théorique).** Étant donné que le composant n'a pas encore de panier chargé (fenêtre très étroite entre le montage du composant et la résolution de `GET /pos/baskets/current`), quand un événement `basket-cancelled` arrive, alors aucun toast n'est affiché et rien n'est vidé — le composant l'ignore silencieusement. *(Note : en pratique, une fois le chargement initial résolu, le bénévole a toujours un panier — même vide — donc ce cas ne peut survenir qu'avant la résolution de la requête initiale.)*

## Tasks / Subtasks

- [x] **Modèle — nouvel événement SSE (AC 1, 3)**
  - [x] `pluribourse-frontend/src/app/models/edition.model.ts` (UPDATE) : ajouter, à côté de `PhaseChangedEvent`
    ```typescript
    export interface BasketCancelledEvent {
      editionId: number;
      newPhase: PhaseType;
    }
    ```
    Deux champs seulement (pas de `previousPhase`) — reflète exactement `BasketCancelledEventDto` côté backend (`shared/sse/BasketCancelledEventDto.java`, Story 2.8).

- [x] **`SseService` — extraire la logique de connexion commune, ajouter `basketCancelled()` (AC 1, 3)**
  - [x] `pluribourse-frontend/src/app/services/sse.service.ts` (UPDATE) : mettre à jour l'import en tête de fichier pour inclure le nouveau type — `import { BasketCancelledEvent, PhaseChangedEvent, PhaseType } from '../models/edition.model';`. `phaseChanges()` et la nouvelle méthode partagent la même mécanique de connexion (`new EventSource('/api/sse/events', { withCredentials: true })`, gestion `onerror`/déconnexion, fermeture au unsubscribe) — **ne pas dupliquer ce bloc**, l'extraire en une méthode privée générique :
    ```typescript
    private listen<T>(eventName: string, isValid: (value: unknown) => value is T): Observable<T> {
      return new Observable(observer => {
        const source = new EventSource('/api/sse/events', { withCredentials: true });
        source.addEventListener(eventName, (event: MessageEvent) => {
          try {
            const parsed: unknown = JSON.parse(event.data);
            if (isValid(parsed)) {
              observer.next(parsed);
            }
          } catch {
            // malformed event — ignore
          }
        });
        source.onerror = () => {
          if (source.readyState === EventSource.CLOSED) {
            this.auth.clearSession();
            this.currentEditionService.currentEdition.set(null);
            this.router.navigate(['/login']);
          }
        };
        return () => source.close();
      });
    }

    phaseChanges(): Observable<PhaseChangedEvent> {
      return this.listen('phase-changed', isPhaseChangedEvent);
    }

    basketCancelled(): Observable<BasketCancelledEvent> {
      return this.listen('basket-cancelled', isBasketCancelledEvent);
    }
    ```
    Ajouter le validateur de type à côté de `isPhaseChangedEvent` (même style, réutilise le `Set<PhaseType>` `PHASE_VALUES`/`isPhaseType` déjà présent dans ce fichier) :
    ```typescript
    function isBasketCancelledEvent(value: unknown): value is BasketCancelledEvent {
      if (typeof value !== 'object' || value === null) {
        return false;
      }
      const candidate = value as Record<string, unknown>;
      return typeof candidate['editionId'] === 'number' && isPhaseType(candidate['newPhase']);
    }
    ```
    Chaque appel à `phaseChanges()`/`basketCancelled()` ouvre sa propre connexion `EventSource` (comportement déjà existant, non modifié par cette extraction) — `PosPageComponent` ouvrira donc une seconde connexion SSE en plus de celle déjà ouverte par `AppLayoutComponent` pour `phaseChanges()` pendant qu'un bénévole est sur `/volunteer/pos`. C'est le même patron que l'existant, pas une régression introduite ici ; ne pas tenter de mutualiser les connexions dans cette story (hors périmètre, aucun AC ne le demande).
  - [x] `pluribourse-frontend/src/app/services/sse.service.spec.ts` (UPDATE) : ajouter au minimum un test « emits parsed BasketCancelledEvent on basket-cancelled message » (même style que le test équivalent pour `phase-changed`, ligne 66). Les scénarios JSON malformé / payload non-objet / fermeture au unsubscribe / reconnexion sont déjà couverts par les tests existants de `phaseChanges()` sur le chemin partagé (`listen()`) — pas besoin de les dupliquer pour `basketCancelled()`.

- [x] **`ScannerInputComponent` — support d'un état désactivé (AC 1, 2)**
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts` (UPDATE) : ajouter `readonly disabled = input(false);` (même patron signal-input que `EmptyStateComponent.actionLoading`, `shared/components/empty-state/empty-state.component.ts:16`). Aucune autre logique JS à ajouter : un `<input>` natif `disabled` ne peut pas recevoir le focus — `refocus()` (`inputEl?.focus()`) devient naturellement un no-op, et le `(keydown)` bindé sur l'input ne peut plus se déclencher puisque l'élément ne peut jamais être la cible active. Ne pas ajouter de garde manuelle redondante dans `onKeydown`/`refocus`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.html` (UPDATE) : ajouter `[disabled]="disabled()"` sur l'`<input matInput>` existant (même convention que `item-catalog.component.html:9`, `[disabled]="isLoading()"`).
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.spec.ts` (UPDATE) : ajouter un test vérifiant que `disabled=true` rend l'attribut `disabled` sur l'élément natif (`fixture.nativeElement.querySelector('input').disabled === true`).

- [x] **`PosPageComponent` — écoute de l'événement, annulation du panier (AC 1, 2, 3)**
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` (UPDATE) :
    - Injecter `SseService` (`private readonly sseService = inject(SseService);`) et importer `takeUntilDestroyed` de `@angular/core/rxjs-interop` (même patron que `AppLayoutComponent`, `layout/app-layout/app-layout.component.ts:83-85`).
    - Ajouter `readonly scannerDisabled = signal(false);`.
    - Dans `ngOnInit()`, à côté de `void this.loadBasket();` :
      ```typescript
      this.sseService.basketCancelled().pipe(
        takeUntilDestroyed(this.destroyRef)
      ).subscribe(() => this.onBasketCancelled());
      ```
    - Nouvelle méthode privée :
      ```typescript
      private onBasketCancelled(): void {
        if (this.basket() === null) {
          // AC 3 — cas théorique : l'événement arrive avant que loadBasket() n'ait résolu.
          return;
        }
        this.basket.set(null);
        this.lastScanIssue.set(null);
        this.scannerDisabled.set(true);
        this.toast.showError(this.translate.instant('volunteer.pos.error.phaseChanged'));
      }
      ```
      **Pourquoi `basket.set(null)` plutôt qu'un panier vide reconstruit :** le template affiche déjà l'état vide via `!basket() || basket()!.items.length === 0` (AC 1 « panier vidé » satisfait), et `null` fait aussi office de garde : `onScan`/`removeItem`/`removeLot`/`openPaymentDialog` retournent tous immédiatement sur `!currentBasket` — aucune nouvelle action locale n'est possible après l'annulation, en plus de la désactivation du champ scanner (défense en profondeur, AC 2).
      **`lastScanIssue.set(null)` est nécessaire, pas cosmétique :** sans cette ligne, un avertissement/erreur de scan affiché juste avant l'annulation (ex. « Article incomplet », bandeau `app-notification-inline`) resterait visible à côté du panier désormais vide et du toast persistant de cancellation — deux messages contradictoires simultanés, alors que l'AC 1 attend un état de panier « entièrement vidé ».
    - **Piège à couvrir explicitement (implicite dans AC 1/AC 2, pas un AC séparé) : une requête déjà en vol au moment de l'annulation ne doit pas « ressusciter » un état après coup — ni le panier, ni un message de scan/erreur périmé qui écraserait le toast persistant.** `onScan`, `removeItem`, `removeLot` capturent `currentBasket` (non nul) *avant* leur `await`, donc si `basket-cancelled` arrive pendant que l'un de ces appels est en vol, sa continuation s'exécute après coup. Deux chemins sont concernés, pas un seul :
      - **Chemin succès** : `this.basket.set(updated)` (et, pour `onScan`, l'écriture de `lastScanIssue`) rétablirait un panier non vide — contredit l'AC 1.
      - **Chemin erreur** : `handleScanError(err)`/`toast.showError(generic)` appellerait `ToastService.showError`, qui **remplace** le toast affiché (`ToastService` n'a qu'un seul slot de toast actif) — le toast persistant « La phase a changé... » serait silencieusement écrasé par un message d'erreur de scan sans rapport, une régression bien pire qu'un simple état visuel incorrect.

      Corriger en plaçant la garde **au tout début de la continuation, dans les deux branches** (juste après la résolution de l'`await`, avant toute écriture d'état) :
      ```typescript
      // dans onScan(), removeItem(), removeLot() :
      try {
        const updated = await firstValueFrom(this.posService.addItem(currentBasket.id, barcode)); // ou removeItem/removeLot
        if (this.scannerDisabled()) {
          return;
        }
        this.basket.set(updated);
        // ... reste de la logique de succès (ex. lastScanIssue pour onScan)
      } catch (err: unknown) {
        if (this.scannerDisabled()) {
          return;
        }
        this.handleScanError(err); // ou toast.showError générique pour removeItem/removeLot
      } finally {
        this.scanInFlight = false; // ou removeInFlight — laisser tel quel, inoffensif une fois le scanner désactivé
      }
      ```
      Ne pas ajouter cette garde à `openPaymentDialog()`/`validate()` : `validate()` a déjà son propre effet de bord serveur (marque les articles vendus) qui ne doit jamais être annulé silencieusement côté client — hors périmètre de cette story, aucun AC ne le couvre, et le bouton « Valider » est de toute façon désactivé dès que `basket()` est `null`.
    - **`lastSale`/`printInvoice` restent inchangés.** Une vente déjà validée (Story 4.5, bouton « Imprimer la facture » visible 30s) est un enregistrement immuable, indépendant du panier courant — un `basket-cancelled` pendant cette fenêtre ne doit ni masquer le bouton ni désactiver `printInvoice()`. Ne pas toucher `lastSale`/`invoiceButtonTimer` dans `onBasketCancelled()`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html` (UPDATE) : passer le nouveau signal au scanner —
    ```html
    <app-scanner-input [disabled]="scannerDisabled()" (barcodeScanned)="onScan($event)" />
    ```

- [x] **i18n (AC 1)**
  - [x] `pluribourse-frontend/public/i18n/fr.json` (UPDATE) : sous `volunteer.pos.error`, ajouter `"phaseChanged": "La phase a changé. Votre panier a été annulé."`.
  - [x] `pluribourse-frontend/public/i18n/en.json` (UPDATE) : sous `volunteer.pos.error`, ajouter `"phaseChanged": "The phase has changed. Your basket was cancelled."`.

- [x] **Tests frontend (AC 1, 2, 3)** — `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts` (UPDATE), même style que les tests existants (mocks `posServiceMock`/`paymentDialogServiceMock`/`toastMock`) :
  - [x] Ajouter `Subject` et `EMPTY` à l'import `rxjs` existant (`import { of, throwError, Subject, EMPTY } from 'rxjs';`).
  - [x] Mock `SseService` piloté par un `Subject<BasketCancelledEvent>` **recréé à chaque test** (ex. `let basketCancelled$: Subject<BasketCancelledEvent>;` réassigné dans un `beforeEach` local avant chaque `it`, ou instancié en tête de chaque test avant l'appel à `createComponent()`) — **ne pas** le définir une seule fois au niveau `describe` et le réutiliser tel quel entre tests : un `Subject` partagé entre tests risquerait de porter des abonnements d'un composant d'un test précédent si le teardown Angular ne s'exécute pas avant l'assertion suivante. `sseServiceMock = { basketCancelled: () => basketCancelled$.asObservable() }`, fourni via `{ provide: SseService, useValue: sseServiceMock }` dans `createComponent()`. `vi.clearAllMocks()` (déjà présent dans le `beforeEach` global) ne réinitialise pas un `Subject` — la recréation par test est le seul moyen fiable d'isoler les scénarios.
  - [x] Avec un panier chargé (`BASKET_WITH_ITEM_1`), émettre sur `basketCancelled$` → `component.basket()` devient `null`, `component.lastScanIssue()` devient `null`, `component.scannerDisabled()` devient `true`, `toastMock.showError` appelé avec le message traduit (AC 1).
  - [x] Cas théorique (AC 3) : `posServiceMock.getCurrentBasket.mockReturnValue(EMPTY)` (observable qui ne résout jamais, intention plus claire qu'un `Subject` non résolu) avant `createComponent()`, puis émettre sur `basketCancelled$` → aucun appel à `toastMock.showError`, `component.scannerDisabled()` reste `false`.
  - [x] Régression ciblée sur le piège documenté ci-dessus, **chemin succès** : démarrer un `addItem()` (mock retournant un `Subject` non résolu), émettre `basket-cancelled` pendant que l'appel est en vol, puis résoudre le `Subject` avec un panier mis à jour → `component.basket()` reste `null` (pas de résurrection).
  - [x] Régression ciblée, **chemin erreur** (celui qui écraserait silencieusement le toast persistant si non gardé) : démarrer un `addItem()` (mock retournant un `Subject` non résolu), émettre `basket-cancelled` pendant que l'appel est en vol (le toast persistant `phaseChanged` s'affiche), puis faire échouer le `Subject` (`.error(...)`) → `toastMock.showError` n'a été appelé **qu'une seule fois** (celui de l'annulation), jamais avec le message d'erreur générique de scan.

## Review Findings

Revue de code effectuée (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor). Acceptance Auditor : 0 violation d'AC (implémentation vérifiée fidèle à chaque AC, prescriptions de la story suivies quasi mot pour mot). 0 decision-needed, 3 patch, 3 defer, 8 rejetés comme bruit (5 faux positifs vérifiés contre le code source réel, 3 nitpicks sans conséquence fonctionnelle).

- [x] [Review][Patch] Le test AC 3 utilise `EMPTY` (rxjs) en croyant qu'il « ne résout jamais » — faux : `EMPTY` se complète immédiatement sans valeur, donc `firstValueFrom` rejette avec une `EmptyError` (rattrapée par le `catch` de `loadBasket()`, qui affiche un toast générique de façon asynchrone après l'assertion du test). Le test passe aujourd'hui par accident de timing, pas par construction — remplacer par `NEVER` (rxjs), le véritable opérateur « ne résout jamais ». Erreur introduite par moi-même lors de la passe de validation de la story (mauvaise description d'`EMPTY`). [pos-page.component.spec.ts:353] **Appliqué** : import et mock remplacés par `NEVER`.
- [x] [Review][Patch] `openPaymentDialog()` n'a pas la garde `scannerDisabled()` dans son bloc `catch` (contrairement à `onScan`/`removeItem`/`removeLot`) — si `basket-cancelled` arrive pendant que le panier de paiement est ouvert ou que `validate()` est en vol, le panier a déjà été supprimé côté serveur par la même transition (Story 2.8) ; la requête `validate()` reçoit donc un 404 `basket-not-found`, non reconnu par `handleValidationError`, qui affiche le toast générique « Impossible de... » — écrasant silencieusement le toast persistant « La phase a changé ». La story avait explicitement exclu cette garde en confondant « ne pas court-circuiter la requête » (correct) et « ne pas protéger le toast contre un écrasement » (erreur). [pos-page.component.ts:183-184] **Appliqué** : garde ajoutée dans le `catch`, nouveau test de régression `does not let a late validate error overwrite the persistent cancellation toast`.
- [x] [Review][Patch] Le nouveau garde `scannerDisabled()` post-`await` (succès et erreur) est identique dans `onScan`/`removeItem`/`removeLot`, mais seul `onScan` a des tests de régression dédiés — `removeItem`/`removeLot` ne sont pas exercés sur ce chemin. Ajouter au moins un test pour `removeItem` (chemin erreur, le plus à risque : écrasement du toast persistant). [pos-page.component.ts:114-133] **Appliqué** : nouveau test `does not let a late remove-item error overwrite the persistent cancellation toast`.
- [x] [Review][Defer] `PosPageComponent.basketCancelled()` et `AppLayoutComponent.phaseChanges()` ouvrent chacun leur propre `EventSource` vers `/api/sse/events` — l'extraction `listen<T>()` partage le code, pas la connexion. Un bénévole sur `/volunteer/pos` maintient donc deux connexions SSE simultanées. Comportement préexistant (chaque appel à `phaseChanges()` ouvrait déjà sa propre connexion avant cette story), cette story l'étend à un second flux sans l'introduire — mutualiser nécessiterait un multicast partagé (`shareReplay({refCount:true})` ou équivalent), hors périmètre de cette story (aucun AC ne le demande, déjà documenté comme tel dans les Dev Notes). [sse.service.ts:38-72] — deferred, pre-existing pattern
- [x] [Review][Defer] Les requêtes HTTP en vol (`addItem`/`removeItem`/`removeLot`) au moment de l'annulation ne sont pas réellement annulées (pas d'`AbortSignal`/`takeUntil`) — seule leur réponse est ignorée côté client une fois arrivée. Impact réel faible : le panier est déjà supprimé côté serveur par la même transition (Story 2.8), donc la requête échoue généralement en 404 sans dommage. Piste d'amélioration réelle mais non bloquante. [pos-page.component.ts:81-133] — deferred, low impact
- [x] [Review][Defer] Le commentaire sur `scannerDisabled` affirme que seul un rechargement complet de page le réinitialise (AC 2) — exact pour un rechargement navigateur, mais une navigation SPA interne (quitter `/volunteer/pos` puis y revenir) détruit et recrée `PosPageComponent` par la stratégie de réutilisation de route par défaut d'Angular, ce qui réinitialise `scannerDisabled` à `false` sans rechargement complet. La sécurité fonctionnelle reste assurée dans ce cas (`loadBasket()` échouerait immédiatement contre la nouvelle phase via `PhaseGuard`, laissant `basket()` à `null` et donc le scanner toujours inopérant via les gardes `!currentBasket` déjà en place) — mais l'AC 2 telle que formulée n'est pas littéralement exacte pour ce chemin. Documenté pour information ; changer ce comportement nécessiterait de retrancher l'AC 2 elle-même (décision produit), hors périmètre d'une revue de code. [pos-page.component.ts:49-51] — deferred, no functional harm identified

**Rejetés comme bruit :** « un bénévole sans panier actif ne serait pas notifié, cas fréquent pas seulement théorique » — faux, vérifié contre `PosBasketService.getOrCreateCurrentBasket` (backend) : un panier est toujours auto-créé dès le premier accès à `/volunteer/pos`, donc `basket()` n'est `null` que pendant la fenêtre sub-seconde avant la résolution de cette requête — exactement le « cas théorique » déjà documenté et testé par l'AC 3, confirmé satisfait par l'Acceptance Auditor · réutilisation de `basket() === null` pour à la fois détecter « pas encore chargé » et supprimer silencieusement un doublon d'événement jugée « fragile » — aucun bug comportemental, la suppression de doublon est le comportement désiré · toast persistant non vérifié comme tel — faux, `ToastService.showError` n'a pas de timer d'auto-dismiss (vérifié dans `toast.service.ts`, confirmé indépendamment par l'Acceptance Auditor) · un scanner physique pourrait contourner `[disabled]` via l'écouteur `@HostListener('document:keydown')` — faux, vérifié dans `scanner-input.component.ts` : ce listener document-wide ne fait que réarmer le timer de refocus, la logique de bufferisation/émission du code-barres vit exclusivement dans `(keydown)` lié à l'`<input>` natif, qui ne peut plus se déclencher une fois désactivé · événement `basket-cancelled` d'une édition différente non filtré — structurellement impossible, une seule édition active à la fois et la Story 2.8 purge déjà tous les paniers d'une édition à chaque transition · `newPhase` validé mais jamais consommé — cohérent avec le patron `isPhaseChangedEvent` déjà en place (valide toute la forme du payload même si un champ n'est pas exploité en aval) · nom `disabled` sur `ScannerInputComponent` jugé ambigu vis-à-vis des reactive forms — même convention déjà utilisée ailleurs (`item-catalog.component.html`), pas un `ControlValueAccessor` · texte i18n du toast jugé pas assez actionnable — texte normatif imposé mot pour mot par EXPERIENCE.md, l'enrichir serait une déviation non autorisée de la spec UX.

### Round 2 (re-revue demandée par l'utilisateur, sur le diff patché)

Acceptance Auditor : 0 violation d'AC, les 3 patchs du round 1 vérifiés réellement présents et corrects. 0 decision-needed, 3 patch, 0 nouveau defer, 12 rejetés comme bruit (dont plusieurs redites du round 1, déjà vérifiées fausses — reconfirmées ici avec le même raisonnement).

- [x] [Review][Patch] **Trou réel manqué au round 1** : `loadBasket()` (méthode privée partagée, appelée à la fois par `ngOnInit()` et par `openPaymentDialog()` après un `validate()` réussi pour recharger le panier neuf) n'a **aucune** garde `scannerDisabled()`, contrairement aux quatre autres méthodes déjà corrigées. Si `basket-cancelled` arrive pendant que ce rechargement post-`validate()` est en vol, `this.basket.set(basket)` (chemin succès) ressusciterait un panier après l'annulation, et `handleScanError(err)` (chemin erreur, ex. 422 si la phase a déjà changé côté serveur) écraserait le toast persistant. Sans risque côté `ngOnInit()` (`scannerDisabled()` y est toujours `false` à ce stade), donc corriger dans `loadBasket()` lui-même plutôt qu'à chaque site d'appel. [pos-page.component.ts:175-182] **Appliqué** : garde ajoutée sur les deux branches de `loadBasket()`.
- [x] [Review][Patch] Le signal `scannerDisabled` sert en réalité de drapeau générique « panier annulé » dans les gardes de `removeItem`/`removeLot`/`openPaymentDialog` (aucun rapport avec le scanner à ces sites d'appel) — nom trompeur qui égarera le prochain lecteur. Renommer en `basketCancelled` (binding template mis à jour en conséquence), aucun changement de comportement. [pos-page.component.ts:52-54] **Appliqué** : renommé partout (composant, template, tests), aucune référence orpheline restante.
- [x] [Review][Patch] `removeLot` a la même garde anti-résurrection que `removeItem` mais aucun test de régression dédié — signalé indépendamment par les deux couches de revue (round 1 et round 2). Ajouter le test manquant, symétrique à celui de `removeItem`. [pos-page.component.ts:136-155] **Appliqué** : nouveau test `does not let a late remove-lot error overwrite the persistent cancellation toast`.

**Rejetés comme bruit (round 2) :** race « `loadBasket()` lent, résout après l'événement » présentée comme perte silencieuse d'annulation — c'est exactement le comportement explicite et accepté de l'AC 3 (« le composant l'ignore silencieusement ») ; de plus, une requête de chargement initial en vol au moment du changement de phase échouerait de toute façon côté serveur via `PhaseGuard.requireSalePhase` (déjà en phase non-Vente), donc aucune résurrection de panier n'est possible même dans ce scénario — et le nouveau patch sur `loadBasket()` ci-dessus la durcit davantage sans rien casser · re-vérification du contournement scanner physique via `@HostListener('document:keydown')` — toujours faux, même preuve qu'au round 1 · re-vérification `editionId`/`newPhase` non exploités — toujours structurellement impossible/cohérent avec le patron existant, mêmes preuves qu'au round 1 · requêtes HTTP en vol non abortées — déjà loggé en defer au round 1, pas un nouveau finding · `printInvoice()` pourrait écraser le toast persistant pendant la fenêtre de 30s — faux : l'impression reste **délibérément** fonctionnelle après annulation (décision de la Story 4.5, confirmée deux fois par l'Acceptance Auditor, `Sale` immuable sans garde de phase) ; un toast de succès sur une action volontaire du bénévole qui écrase un toast d'information périmé est un comportement UX normal, pas un défaut · panier « ressuscité » via `openPaymentDialog()` réouvert après une résurrection par `loadBasket()` — résolu en effet de bord par le patch sur `loadBasket()` ci-dessus, pas de correctif indépendant nécessaire · placement de la clé i18n `phaseChanged` sous `error.*` jugé incohérent — cohérent avec la réutilisation déjà existante d'`error.generic` pour des échecs non liés au scan (`removeItem`/`removeLot`) · duplication des 4 tests de régression (structure quasi identique) sans helper partagé — raisonnable mais optionnel, CLAUDE.md décourage l'abstraction prématurée pour ce volume de tests · duplication de `isBasketCancelledEvent`/`isPhaseChangedEvent` (2 lignes de vérification de forme) — extraction non justifiée pour un contrôle aussi court · test AC 3 n'exerçant pas l'état DOM du scanner pendant le chargement initial — couverture redondante, déjà vérifiée par le test dédié « propagates scannerDisabled ».

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- **`SseService.phaseChanges()` et son patron de connexion `EventSource`/`onerror`** (`services/sse.service.ts`) portent déjà toute la mécanique SSE — cette story l'étend (extraction en `listen<T>()` partagé) plutôt que d'écrire une seconde implémentation parallèle.
- **`BasketCancelledEventDto` (backend) et le format exact du payload `{editionId, newPhase}`** sont déjà fixés et testés par la Story 2.8 (`done`) — ne pas réinventer la structure côté frontend, `BasketCancelledEvent` doit correspondre exactement.
- **`ToastService.showError(message)`** (`shared/components/toast/toast.service.ts`) est déjà le mécanisme de toast **persistant** du projet (pas d'auto-dismiss contrairement à `showSuccess`, bouton « Fermer » déjà rendu par `toast-container.component.html` quand `type === 'error'`, `role="alert"`/`aria-live="assertive"`). C'est exactement le comportement requis par l'AC 1 — ne pas créer un nouveau type de toast ni un composant dédié.
- **Le guard `!currentBasket` déjà présent dans `onScan`/`removeItem`/`removeLot`/`openPaymentDialog`** (`pos-page.component.ts`) bloque déjà toute action une fois `basket.set(null)` appelé — s'appuyer dessus plutôt que d'ajouter un état `basketCancelled: boolean` séparé et redondant.
- **`getOrCreateCurrentBasket` (backend, Story 4.2) crée toujours un panier** dès le premier appel de `GET /pos/baskets/current` — c'est pourquoi le cas « pas de panier actif » (AC 3) est correctement documenté comme théorique : après résolution de `loadBasket()`, `basket()` n'est jamais `null` en usage normal.

### Écarts de documentation identifiés — non bloquants

- **`architecture.md` § Événements SSE documente `basket-cancelled` avec le payload `{reason: "phase-changed"}`** — obsolète, déjà signalé et tranché en faveur d'`epics.md`/du code réel par la Story 2.8 (Dev Notes). Le payload réel est `{editionId, newPhase}` (`BasketCancelledEventDto`). Ne pas suivre `architecture.md` sur ce point précis.
- **`epics.md` Story 4.6 AC 1 cite un tag `UX-DR21`** qui ne correspond à aucune ancre trouvable dans `ux-designs/ux-PluriBourse-2026-06-09/` (ni `DESIGN.md` ni `EXPERIENCE.md` ne contiennent cette référence). Le texte et le comportement du toast sont néanmoins bien normatifs et vérifiés mot pour mot dans `EXPERIENCE.md` (tableau des micro-interactions, ligne « Changement de phase pendant une transaction »). Traiter `UX-DR21` comme une référence informelle non résolue, pas comme une exigence supplémentaire à chercher.

### Project Structure Notes

- Aucun nouveau composant/service — uniquement des UPDATE sur des fichiers frontend existants : `models/edition.model.ts`, `services/sse.service.ts` (+ spec), `features/volunteer/pos/scanner-input.component.ts/.html` (+ spec), `features/volunteer/pos/pos-page.component.ts/.html` (+ spec), `public/i18n/fr.json`/`en.json`.
- **Aucun changement backend.** Story 2.8 (`done`) a déjà tout livré côté serveur — ne pas toucher à `EditionService`, `BasketCancelledEventDto`, `SseEmitterRegistry`, ni aux tests backend.
- Cohérent avec la table de correspondance fonctionnalité → structure d'`architecture.md` : `F4 — POS` → `components/pos/`, `services/pos.service.ts` ; le service SSE lui-même est déjà en dehors de ce mapping spécifique (`services/sse.service.ts`, consommé transversalement par `layout/` et, désormais, `features/volunteer/pos/`).

### Fichiers à lire avant modification

- `pluribourse-frontend/src/app/services/sse.service.ts`, `sse.service.spec.ts` (référence directe — patron à étendre)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts` (référence — patron `takeUntilDestroyed` + souscription SSE dans `ngOnInit`)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts/.html/.spec.ts` (UPDATE — lire intégralement, notamment les guards `!currentBasket` déjà en place dans les quatre méthodes async)
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts/.html/.spec.ts` (UPDATE — lire intégralement, notamment `refocus()`/`onKeydown` pour confirmer qu'aucune garde manuelle supplémentaire n'est nécessaire une fois `disabled` posé sur l'`<input>` natif)
- `pluribourse-frontend/src/app/models/edition.model.ts` (référence — patron `PhaseChangedEvent`/`isPhaseType` à suivre pour `BasketCancelledEvent`)
- `pluribourse-frontend/src/app/shared/components/toast/toast.service.ts`, `toast-container.component.html` (référence — confirmer le comportement persistant de `showError`, ne pas le modifier)
- `pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.ts` (référence — seul précédent de `input()` signal dans ce codebase, patron à reproduire pour `ScannerInputComponent.disabled`)
- `pluribourse-frontend/public/i18n/fr.json`, `en.json` (UPDATE — ajout sous `volunteer.pos.error`, ne pas toucher aux autres clés `volunteer.pos.*`)
- `pluribourse-backend/src/main/java/org/pluribourse/shared/sse/BasketCancelledEventDto.java` (référence, lecture seule — format exact du payload à reproduire côté TypeScript)
- `_bmad-output/implementation-artifacts/2-8-annulation-du-panier-lors-dune-transition-de-phase-cote-serveur.md` (référence directe — Dev Notes sur le payload, la portée du broadcast global, et le rappel explicite que la réception Angular est le périmètre de cette story)

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.6] — ACs source (FR-090), dépendance explicite sur la Story 2.8
- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.8] — note de dev bornant le périmètre backend/frontend entre les deux stories
- [Source: _bmad-output/planning-artifacts/architecture.md#Notification de Changement de Phase (FR-090)] — mécanisme SSE, `EventSource` encapsulé, reconnexion automatique
- [Source: _bmad-output/planning-artifacts/architecture.md#Événements SSE] — payload documenté obsolète pour `basket-cancelled`, voir Dev Notes § Écarts
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md] — texte exact du toast et comportement (« Changement de phase pendant une transaction » — toast persistant, panier vidé, scanner désactivé jusqu'à rechargement)
- [Source: _bmad-output/implementation-artifacts/2-8-annulation-du-panier-lors-dune-transition-de-phase-cote-serveur.md] — payload réel `{editionId, newPhase}`, portée globale du broadcast (pas de ciblage par utilisateur/panier), confirmation que ce cas « aucun panier actif » est un cas théorique déjà anticipé
- [Source: _bmad-output/implementation-artifacts/4-5-impression-de-la-facture-acheteur.md] — `lastSale`/fenêtre 30s indépendants du cycle panier, à ne pas coupler à cette story
- [Source: pluribourse-frontend/src/app/services/sse.service.ts, sse.service.spec.ts] — lus intégralement
- [Source: pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts] — lu intégralement
- [Source: pluribourse-frontend/src/app/features/volunteer/pos/**] — lus intégralement
- [Source: pluribourse-frontend/src/app/models/pos.model.ts, edition.model.ts] — lus intégralement
- [Source: pluribourse-frontend/src/app/shared/components/toast/**] — lus intégralement
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/sse/BasketCancelledEventDto.java] — lu intégralement

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story)

### Debug Log References

Implémentation directe sans écart par rapport au plan de la story — aucun comportement non anticipé rencontré. `npm test -- --watch=false` (suite complète) : 526/526 tests frontend (56 fichiers), aucune régression. `npm run build` (production) : aucune erreur TypeScript. Aucun changement backend, suite backend non concernée.

### Completion Notes List

- `edition.model.ts` : nouvelle interface `BasketCancelledEvent` (`{editionId, newPhase}`), reflète exactement `BasketCancelledEventDto` (Story 2.8).
- `sse.service.ts` : logique de connexion `EventSource` extraite en méthode privée générique `listen<T>()`, réutilisée par `phaseChanges()` (inchangée en comportement) et la nouvelle `basketCancelled()`. Nouveau validateur `isBasketCancelledEvent`.
- `scanner-input.component.ts/.html` : nouveau signal `disabled = input(false)`, lié à `[disabled]` sur l'`<input>` natif — aucune garde JS supplémentaire nécessaire (un input natif désactivé ne peut pas recevoir le focus, `refocus()`/`onKeydown` deviennent naturellement des no-op).
- `pos-page.component.ts/.html` : nouveau signal `scannerDisabled`, souscription à `sseService.basketCancelled()` dans `ngOnInit()` (`takeUntilDestroyed`), nouvelle méthode privée `onBasketCancelled()` (vide le panier et `lastScanIssue`, désactive le scanner, affiche le toast persistant `volunteer.pos.error.phaseChanged` — ignore silencieusement si `basket()` est encore `null`, cas théorique AC 3). Garde `scannerDisabled()` ajoutée dans `onScan`/`removeItem`/`removeLot`, sur les deux branches (succès et erreur) de leur continuation post-`await`, pour qu'une requête déjà en vol au moment de l'annulation ne puisse ni résusciter le panier ni faire apparaître un toast d'erreur périmé qui écraserait silencieusement le toast persistant de cancellation (`ToastService` n'a qu'un seul slot actif). `lastSale`/`printInvoice`/le timer 30s (Story 4.5) volontairement non touchés.
- i18n : clé `volunteer.pos.error.phaseChanged` ajoutée en FR et EN.
- Tests : 1 nouveau test `sse.service.spec.ts` (basket-cancelled), 1 nouveau test `scanner-input.component.spec.ts` (attribut `disabled` natif), 5 nouveaux tests `pos-page.component.spec.ts` (AC 1 — panier/lastScanIssue vidés + scanner désactivé + toast persistant ; propagation du signal au DOM enfant ; AC 3 — cas théorique sans panier chargé ; régression chemin succès — pas de résurrection du panier ; régression chemin erreur — pas d'écrasement du toast persistant).
- Suite complète re-validée : **526/526 tests frontend** (56 fichiers), aucune régression. Build de production sans erreur. Aucun changement backend (confirmé par la story, Story 2.8 ayant déjà tout livré côté serveur).
- Revue de code (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) : Acceptance Auditor confirme 0 violation d'AC. 0 decision-needed, 3 patch appliqués — test AC 3 corrigé (`EMPTY` → `NEVER`, l'erreur venait de ma propre passe de validation de la story), garde `scannerDisabled()` ajoutée au `catch` d'`openPaymentDialog()` (empêchait le toast persistant d'être écrasé par une erreur 404 tardive de `validate()`), 2 nouveaux tests de régression (`removeItem`, `validate()`). 3 defer documentés dans `deferred-work.md` (deux connexions SSE simultanées, requêtes HTTP en vol non abortées, `scannerDisabled` réinitialisable par navigation SPA interne). 8 rejetés comme bruit (5 faux positifs vérifiés contre le code source réel, 3 nitpicks sans conséquence). Suite complète re-validée après patchs : **528/528 tests frontend**, aucune régression.
- Re-revue de code (round 2, demandée par l'utilisateur, sur le diff patché) : Acceptance Auditor confirme à nouveau 0 violation, les 3 patchs du round 1 vérifiés réellement présents. 0 decision-needed, 3 nouveaux patch appliqués — trou réel manqué au round 1 : `loadBasket()` (méthode partagée, aussi appelée après un `validate()` réussi) n'avait aucune garde anti-résurrection, corrigé sur ses deux branches ; renommage `scannerDisabled` → `basketCancelled` (le signal sert de drapeau générique « panier annulé » dans 3 méthodes sans rapport avec le scanner, aucun changement de comportement) ; test de régression manquant sur `removeLot` ajouté. 12 rejetés comme bruit round 2 (dont plusieurs redites du round 1 déjà vérifiées fausses, et la découverte que `printInvoice()` reste intentionnellement fonctionnel après annulation — décision Story 4.5, pas un défaut). Suite complète re-validée : **529/529 tests frontend**, build de production sans erreur, aucune régression.

### File List

**Frontend — UPDATE**
- `pluribourse-frontend/src/app/models/edition.model.ts` — nouvelle interface `BasketCancelledEvent`
- `pluribourse-frontend/src/app/services/sse.service.ts` — extraction `listen<T>()`, nouvelle méthode `basketCancelled()`, validateur `isBasketCancelledEvent`
- `pluribourse-frontend/src/app/services/sse.service.spec.ts` — nouveau test `basketCancelled()`
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts` — signal `disabled`
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.html` — binding `[disabled]`
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.spec.ts` — nouveau test `disabled`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` — signal `basketCancelled` (renommé depuis `scannerDisabled`), souscription SSE, `onBasketCancelled()`, gardes anti-résurrection dans `onScan`/`removeItem`/`removeLot`/`openPaymentDialog`/`loadBasket` (garde `loadBasket` ajoutée en round 2)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html` — binding `[disabled]` sur `app-scanner-input`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts` — 8 nouveaux tests (5 initiaux + 2 round 1 + 1 round 2)
- `pluribourse-frontend/public/i18n/fr.json` — clé `volunteer.pos.error.phaseChanged`
- `pluribourse-frontend/public/i18n/en.json` — clé `volunteer.pos.error.phaseChanged`

## Change Log

- 2026-08-14 — dev-story : implémentation complète (extraction `SseService.listen<T>()` + `basketCancelled()`, signal `disabled` sur `ScannerInputComponent`, gestion de l'annulation dans `PosPageComponent` avec garde anti-résurrection sur requêtes en vol, i18n FR/EN). Aucun écart par rapport au plan de la story. 526/526 tests frontend, build de production sans erreur, aucune régression. Aucun changement backend. Statut → review.
- 2026-08-14 — code-review (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) : Acceptance Auditor confirme conformité totale aux 3 AC, 0 violation. 0 decision-needed, 3 patch appliqués (test AC 3 `EMPTY`→`NEVER`, garde `scannerDisabled()` sur `openPaymentDialog()`, tests de régression manquants sur `removeItem`/`validate()`), 3 defer documentés dans `deferred-work.md` (deux connexions SSE simultanées — préexistant, requêtes en vol non abortées — impact faible, `scannerDisabled` réinitialisable par navigation SPA interne — aucun préjudice fonctionnel identifié), 8 rejetés comme bruit (5 faux positifs vérifiés contre le code source réel : panier toujours auto-créé donc « pas de panier actif » reste théorique, toast bien persistant, scanner physique bien bloqué par l'input désactivé, événement cross-édition structurellement impossible, `newPhase` non consommé cohérent avec le patron existant). 528/528 tests frontend re-validés après patchs, aucune régression. Statut → done.
- 2026-08-14 — re-code-review (round 2, demandée par l'utilisateur) : Acceptance Auditor reconfirme conformité totale, 0 violation, et vérifie que les 3 patchs du round 1 sont bien présents. 0 decision-needed, 3 nouveaux patch appliqués (garde anti-résurrection manquante sur `loadBasket()` — trou réel du round 1, renommage `scannerDisabled`→`basketCancelled` pour un nom fidèle à son usage réel, test de régression manquant sur `removeLot`), 0 nouveau defer, 12 rejetés comme bruit (majoritairement des redites du round 1 déjà vérifiées fausses, plus la confirmation que `printInvoice()` reste intentionnellement actif après annulation par design Story 4.5). 529/529 tests frontend re-validés, build de production sans erreur, aucune régression. Statut → done (confirmé).
