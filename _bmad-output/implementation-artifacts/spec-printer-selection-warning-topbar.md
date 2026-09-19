---
title: 'Avertissement imprimante(s) manquante(s) dans le topbar'
type: 'feature'
created: '2026-09-19'
status: 'done'
context: []
baseline_commit: 'a013d570858aa370396357ef4efc61b67362d415'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Un bénévole peut arriver sur `/volunteer` sans jamais être passé par `/printer-selection` (aucun guard ne l'y force) ; il n'a alors aucun signal visuel qu'il lui manque une imprimante thermique et/ou A4 tant qu'il ne tente pas une impression qui échoue.

**Approach:** Ajouter une icône d'avertissement ancrée en haut à droite du badge de phase (topbar), visible uniquement pour un bénévole, avec un tooltip précisant ce qui manque (thermique, A4, ou les deux). L'état est lu via l'endpoint existant `GET /api/printers/selection` (déjà autorisé pour VOLUNTEER, aucun changement backend) et rafraîchi à chaque navigation pour disparaître dès que le bénévole complète sa sélection sur `/printer-selection`.

## Boundaries & Constraints

**Always:**
- Une imprimante est considérée "manquante" dès que `thermalPrinterId` ou `a4PrinterId` vaut `null` dans la réponse — y compris si le bénévole a explicitement choisi "Aucune" (le modèle de données actuel, session-only, ne distingue pas "jamais sélectionné" de "Aucune choisi délibérément" ; voir `PrinterSelectionService.getStatus()`). Décision assumée : mieux vaut un faux positif occasionnel qu'un bénévole qui ne sait pas qu'il ne peut pas imprimer.
- Visible uniquement quand `isVolunteer()` est vrai — jamais pour un admin.
- N'afficher l'icône qu'une fois le statut chargé (ne jamais l'afficher pendant l'état "non encore chargé", pour éviter un flash au montage).
- Le warning reste indépendant de `currentEdition()` — contrairement au phase-chip, la sélection d'imprimante n'a pas de sens borné à une édition active (un bénévole peut arriver avant toute édition active).
- Réutiliser `MatTooltipModule`/`MatIconModule` déjà importés dans `AppLayoutComponent`. Aucune nouvelle dépendance.
- Tous les textes passent par ngx-translate (`fr.json`/`en.json`), aucune chaîne codée en dur.

**Ask First:** Aucune décision supplémentaire identifiée à ce stade.

**Never:**
- Ne pas vérifier la connectivité réseau/PrinterBridge de l'imprimante sélectionnée (hors périmètre, propriété de la Story 3.15 / `/admin/printers`).
- Ne pas créer de nouvel endpoint backend — `GET /api/printers/selection` suffit.
- Ne pas bloquer la navigation ni forcer une redirection vers `/printer-selection` — c'est un avertissement informatif, pas un guard.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Sélection complète | `{thermalPrinterId: 3, a4PrinterId: 7}` | Aucune icône affichée | N/A |
| Thermique manquante | `{thermalPrinterId: null, a4PrinterId: 7}` | Icône affichée, tooltip "imprimante thermique manquante" | N/A |
| A4 manquante | `{thermalPrinterId: 3, a4PrinterId: null}` | Icône affichée, tooltip "imprimante A4 manquante" | N/A |
| Les deux manquantes | `{thermalPrinterId: null, a4PrinterId: null}` | Icône affichée, tooltip "imprimantes thermique et A4 manquantes" | N/A |
| Admin connecté | rôle ADMIN, sélection incomplète | Aucune icône affichée, quel que soit l'état | N/A |
| Retour depuis `/printer-selection` | bénévole complète sa sélection puis navigue vers `/volunteer` | Icône disparaît (statut rechargé à la navigation) sans reload de page | N/A |
| Appel `GET /api/printers/selection` échoue | erreur réseau/HTTP | Aucune icône affichée (fail-open, cohérent avec `CurrentEditionService.loadEdition()` qui absorbe déjà ses erreurs) | Erreur avalée silencieusement, pas de toast (non-bloquant, évite le bruit) |

</frozen-after-approval>

## Code Map

- `pluribourse-frontend/src/app/services/print.service.ts` -- ajouter `getSelection()`, GET vers l'endpoint déjà existant
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts` -- charger et exposer le statut de sélection (signal), computed thermal/a4 manquants, rafraîchi sur `NavigationEnd`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` -- icône d'avertissement ancrée sur `.phase-chip`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss` -- positionnement absolu haut-droite de l'icône
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` -- couverture des scénarios de la matrice
- `pluribourse-frontend/public/i18n/fr.json`, `pluribourse-frontend/public/i18n/en.json` -- clés `nav.printerWarning.{thermalMissing,a4Missing,bothMissing}`

## Tasks & Acceptance

**Execution:**
- [x] `print.service.ts` -- ajouter `getSelection(): Observable<PrinterSelectionStatus> { return this.http.get<PrinterSelectionStatus>('/api/printers/selection'); }` -- réutilise le DTO déjà défini dans `printer.model.ts`, symétrique à `submitSelection()`
- [x] `app-layout.component.ts` -- injecter `PrintService` ; signal `printerSelectionStatus = signal<PrinterSelectionStatus | null>(null)` ; méthode privée `loadPrinterSelectionStatus()` qui appelle `getSelection()` et set le signal (catch silencieux, cf. edge case erreur) ; dans `ngOnInit`, si `isVolunteer()`, appeler une première fois puis s'abonner à `router.events` filtré sur `NavigationEnd` (`takeUntilDestroyed`) pour rappeler à chaque navigation ; deux `computed` : `missingThermalPrinter`/`missingA4Printer` (`true` seulement si le statut est chargé ET l'id correspondant est `null`) -- plus un troisième `computed` `printerWarningKey` qui centralise le choix de la clé i18n (thermique/A4/les deux) hors du template
- [x] `app-layout.component.html` -- dans `.topbar__center`, envelopper le bloc `phase-chip` existant (les 3 branches `@if`/`@else`) dans un conteneur `.phase-chip-wrap` ; ajouter un `@if (isVolunteer() && printerWarningKey(); as warningKey)` avec un `<span class="printer-warning" tabindex="0" [matTooltip]="..." [attr.aria-label]="...">` contenant `<mat-icon aria-hidden="true">warning</mat-icon>`
- [x] `app-layout.component.scss` -- `.phase-chip-wrap { position: relative; display: inline-flex; }` et `.printer-warning { position: absolute; top: -6px; right: -6px; color: var(--pb-on-warning-container); ... }` (icône 18px, cohérent avec les icônes de sidebar)
- [x] `fr.json`/`en.json` -- ajouté `nav.printerWarning.thermalMissing`, `.a4Missing`, `.bothMissing` (fr et en), parité vérifiée : 610/610 clés de part et d'autre
- [x] `app-layout.component.spec.ts` -- mock `PrintService` (`getSelection: vi.fn()`) ajouté aux providers ; 8 nouveaux tests couvrant chaque ligne de la matrice I/O plus la disparition de l'icône après navigation une fois la sélection complétée

**Acceptance Criteria:**
- Given un bénévole connecté sans sélection d'imprimante, when il arrive sur n'importe quelle page de l'application, then une icône d'avertissement est visible en haut à droite du badge de phase avec un tooltip listant précisément ce qui manque
- Given un bénévole avec les deux imprimantes déjà sélectionnées, when il navigue dans l'application, then aucune icône d'avertissement n'apparaît
- Given un admin connecté avec une sélection d'imprimante incomplète, when il consulte le topbar, then aucune icône d'avertissement n'apparaît
- Given un bénévole voit l'avertissement, when il complète sa sélection sur `/printer-selection` puis revient sur une autre page, then l'avertissement disparaît sans rechargement manuel de la page

## Verification

**Commands:**
- `cd pluribourse-frontend && npm test` -- expected: tous les tests passent, y compris les nouveaux scénarios de `app-layout.component.spec.ts`
- `cd pluribourse-frontend && npm run build` -- expected: build sans warning

**Manual checks (if no CLI):**
- Vérification visuelle par Manerial : se connecter en tant que bénévole sans sélection d'imprimante, observer l'icône et son tooltip, puis compléter la sélection et vérifier sa disparition.

## Suggested Review Order

**État & chargement (design cœur)**

- Point d'entrée : pipeline `switchMap` déclenchée par `NavigationEnd` (`startWith(null)` pour le chargement initial) — annule toute requête en vol pour éviter qu'une réponse obsolète n'écrase une réponse plus récente.
  [`app-layout.component.ts:133`](../../pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts#L133)

- Signal + 3 `computed` dérivés (thermique manquant / A4 manquant / clé i18n du tooltip) — logique hors template, testable isolément.
  [`app-layout.component.ts:65`](../../pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts#L65)

- Nouvel appel réutilisant l'endpoint déjà autorisé pour VOLUNTEER, aucun nouveau endpoint backend.
  [`print.service.ts:14`](../../pluribourse-frontend/src/app/services/print.service.ts#L14)

**Liaison UI**

- Icône ancrée en haut à droite du badge de phase, `role="img"` + tooltip + aria-label, visible seulement si `isVolunteer() && printerWarningKey()`.
  [`app-layout.component.html:31`](../../pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html#L31)

- Positionnement absolu du badge (`z-index` explicite pour éviter un chevauchement futur).
  [`app-layout.component.scss:67`](../../pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss#L67)

**i18n**

- 3 clés `nav.printerWarning.*` (thermique / A4 / les deux), parité fr/en vérifiée (610/610).
  [`fr.json:82`](../../pluribourse-frontend/public/i18n/fr.json#L82)
  [`en.json:82`](../../pluribourse-frontend/public/i18n/en.json#L82)

**Tests**

- 8 nouveaux tests couvrant chaque ligne de la matrice I/O, plus la disparition de l'icône après navigation.
  [`app-layout.component.spec.ts:214`](../../pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts#L214)
