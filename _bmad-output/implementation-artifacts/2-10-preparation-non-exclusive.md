---
baseline_commit: 27bc97f7f11a0830b4b55162ad433e7354afaedb
---

# Story 2.10: Préparation non exclusive

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an administrateur gérant plusieurs éditions,
I want pouvoir créer et préparer une nouvelle édition (catégories, taux de commission, dates) pendant qu'une édition précédente est encore en Dépôt, Vente ou Post-vente,
so that je puisse anticiper la prochaine bourse sans attendre la clôture complète de la précédente.

## Acceptance Criteria

1. **Création libre en Préparation, même avec une édition déjà active (FR-010 amendé).** Étant donné qu'une édition existe déjà en phase Dépôt, Vente ou Post-vente, quand l'admin crée une nouvelle édition (`POST /api/admin/editions`), alors la création réussit (201) et la nouvelle édition démarre en phase Préparation — l'admin peut avoir autant d'éditions en Préparation simultanément qu'il le souhaite.

2. **Blocage à la transition Préparation → Dépôt si une autre édition occupe déjà la zone active (FR-105, nouveau).** Étant donné qu'une édition A est en phase Dépôt, Vente ou Post-vente et qu'une édition B est en Préparation avec ses catégories déjà configurées, quand l'admin déclenche l'avancement de B (`POST /api/admin/editions/{B}/phase/advance`), alors la requête échoue avec 422 `edition-already-active` et B reste en Préparation.

3. **Le blocage se lève dès que l'édition occupant la zone active en sort (ex. Clôture).** Étant donné que l'édition A vient d'être Clôturée (phase CLOSED, plus aucune édition en Dépôt/Vente/Post-vente), quand l'admin déclenche l'avancement de B (Préparation, catégories déjà configurées) vers Dépôt, alors la transition réussit (200) et B passe en Dépôt.

4. **`PhaseType.ACTIVE` n'inclut plus PREPARATION — toute résolution de « l'édition active » via ce filtre ignore les éditions en Préparation.** Étant donné qu'une (ou plusieurs) édition(s) existent uniquement en phase Préparation — aucune en Dépôt/Vente/Post-vente —, quand un client interroge `GET /api/editions/current`, alors la réponse est 404 `no-active-edition`. Conséquence directe côté frontend : la bannière de phase du topbar et la section « Édition active » de la sidebar admin (catalogue, vendeurs, solde) restent masquées tant qu'aucune édition n'a atteint le Dépôt, même si une ou plusieurs éditions sont en cours de préparation.

5. **Une édition en Préparation reste pleinement gérable par son ID explicite, indépendamment de la disparition de la notion d'« édition courante » en Préparation.** Étant donné plusieurs éditions coexistant en Préparation, quand l'admin ouvre `/admin/editions`, alors toutes apparaissent dans la liste (non filtrée par phase) et chacune reste modifiable, supprimable et configurable (catégories, via `/admin/editions/{id}/categories`) indépendamment des autres — ces opérations ciblent déjà l'édition par ID explicite, jamais par « édition active ».

## Tasks / Subtasks

- [x] **Backend — `PhaseType.ACTIVE` : retirer PREPARATION (AC 1, 2, 3, 4)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/PhaseType.java` (UPDATE) :
    ```java
    public static final List<PhaseType> ACTIVE = List.of(DEPOSIT, SALE, POST_SALE);
    ```
    (était `List.of(PREPARATION, DEPOSIT, SALE, POST_SALE)`).
  - **Pourquoi c'est sûr :** tous les appelants existants de `EditionService.getActiveEdition()`/`getActiveEditionDto()` (`SettlementService`, `SellerService` — dont `requireDepositPhase` déjà DEPOSIT-only —, `PosScanService`, `PosBasketService`, `LotService`, `ItemService`, `ItemCatalogService`, `AdminReportController`, `DailySalesReportPrintService`, `DepositValidationService`, `SettlementReportPrintService` — `printReport`/`printAllReports`, déjà gardés par `PhaseGuard.requirePostSalePhase` —, `CurrentEditionCategoryController`, `CurrentEditionController`) sont déjà des opérations qui n'ont de sens qu'à partir du Dépôt — aucune n'est censée réussir pendant la Préparation. Vérifié composant par composant lors de la création de cette story (grep exhaustif sur tout `pluribourse-backend/src/main`), pas seulement supposé.

- [x] **Backend — `EditionAlreadyActiveException` : message mis à jour (AC 2)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/exception/EditionAlreadyActiveException.java` (UPDATE) — le message actuel (« An edition is already active. Close the current edition before creating a new one. ») décrit l'ancien déclencheur (création). Remplacer par :
    ```java
    public EditionAlreadyActiveException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-already-active",
                "Another edition is already in Deposit, Sale or Post-sale phase. It must reach Closed before this one can start Deposit.");
    }
    ```
    Le slug `edition-already-active` (utilisé par le frontend pour router l'erreur) ne change pas — seul le message JavaDoc/detail RFC 7807 change de sens.

- [x] **Backend — `EditionService.createEdition` : retirer le contrôle d'exclusivité (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java` (UPDATE) — dans `createEdition`, supprimer entièrement :
    ```java
    if (repository.existsByPhaseIn(PhaseType.ACTIVE)) {
        throw new EditionAlreadyActiveException();
    }
    ```
    La méthode ne garde que le contrôle `AssociationNameNotConfiguredException` avant la construction de l'entité.

- [x] **Backend — `EditionService.advancePhase` : nouveau contrôle sur la transition Préparation → Dépôt (AC 2, 3)**
  - [x] Même fichier, dans `advancePhase`, ajouter le contrôle **avant** les deux contrôles existants (catégories, bénévole) — même position relative que l'ancien contrôle de `createEdition`, juste déplacé :
    ```java
    @Transactional
    public EditionDto advancePhase(Long id) {
        Edition edition = findById(id);
        PhaseType previousPhase = edition.getPhase();
        PhaseType newPhase = computeNextPhase(previousPhase);
        if (newPhase == PhaseType.DEPOSIT && repository.existsByPhaseIn(PhaseType.ACTIVE)) {
            throw new EditionAlreadyActiveException();
        }
        if (newPhase == PhaseType.DEPOSIT && !editionCategoryRepository.existsByEditionId(id)) {
            throw new NoCategoriesConfiguredException();
        }
        if (newPhase == PhaseType.DEPOSIT && !userRepository.existsByRole(Role.VOLUNTEER)) {
            throw new NoVolunteerConfiguredException();
        }
        return savePhaseThenSendEvent(id, edition, newPhase, previousPhase);
    }
    ```
    **Pourquoi `existsByPhaseIn(PhaseType.ACTIVE)` ne matche jamais l'édition elle-même :** au moment de ce contrôle, `edition` (id=`id`) est encore en PREPARATION — phase désormais absente de `PhaseType.ACTIVE` (tâche précédente) — donc la requête ne peut porter que sur une *autre* édition. Aucune exclusion explicite par ID n'est nécessaire.

- [x] **Backend — test d'intégration `PhaseTransitionIT` : 2 nouveaux scénarios FR-105 + renumérotation (AC 2, 3)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/PhaseTransitionIT.java` (UPDATE) — insérer 2 nouvelles méthodes `@Test` et renuméroter les `@Order` existants selon cette table exacte (le storyboard reste autrement inchangé, données persistantes entre méthodes) :

    | Ancien `@Order` | Nouveau `@Order` | Méthode |
    |---|---|---|
    | 1 | 1 | `create_edition_in_preparation` |
    | 2 | 2 | `get_by_id_returns_edition` |
    | 3 | 3 | `rollback_from_preparation_returns_422` |
    | 4 | 4 | `advance_to_deposit_without_categories_returns_422` |
    | 5 | 5 | `advance_to_deposit_locks_commission_rate` |
    | — | **6 (NEW)** | `advance_to_deposit_blocked_when_another_edition_already_active` |
    | 6 | 7 | `commission_rate_update_rejected_in_deposit` |
    | 7 | 8 | `rollback_deposit_to_preparation_unlocks_commission` |
    | 8 | 9 | `advance_through_all_phases_to_closed` |
    | — | **10 (NEW)** | `advance_to_deposit_unblocked_once_other_edition_closed` |
    | 9 | 11 | `advance_from_closed_returns_422` |
    | 10 | 12 | `rollback_from_closed_to_post_sale_succeeds_when_not_archived` |
    | 11 | 13 | `rollback_from_closed_blocked_when_archived` |
    | 12 | 14 | `volunteer_cannot_trigger_phase_transitions` |
    | 13 | 15 | `unauthenticated_request_returns_401` |
    | 14 | 16 | `get_by_id_returns_404_for_nonexistent_edition` |
    | 15 | 17 | `sse_endpoint_accessible_by_authenticated_admin` |
    | 16 | 18 | `sse_endpoint_accessible_by_volunteer` |
    | 17 | 19 | `sse_endpoint_returns_401_for_unauthenticated` |
    | 18 | 20 | `multiple_phase_changes_are_all_delivered_over_the_same_sse_connection` |

    Nouveau `@Order(6)` — `editionId` vient de passer en DEPOSIT à l'`@Order(5)` précédent :
    ```java
    @Test
    @Order(6)
    void advance_to_deposit_blocked_when_another_edition_already_active() throws Exception {
        // FR-105 (Story 2.10) : editionId est déjà en DEPOSIT (Order 5) — une deuxième édition qui
        // tente de l'atteindre pendant ce temps doit être refusée, même si rien ne bloquait sa
        // création ni sa configuration en Préparation (FR-010 amendé — plusieurs éditions peuvent
        // coexister en Préparation).
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Suivante", null, null, null, null, false, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"))
                .andReturn();
        Long secondEditionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(put("/api/admin/editions/" + secondEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Livres", List.of(3))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + secondEditionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/edition-already-active")));

        Edition edition = repository.findById(secondEditionId).orElseThrow();
        assertThat(edition.getPhase()).isEqualTo(PhaseType.PREPARATION);

        // Nettoyage — toujours en PREPARATION, suppression autorisée, ne doit pas persister
        // jusqu'aux étapes suivantes du storyboard.
        mockMvc.perform(delete("/api/admin/editions/" + secondEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }
    ```

    Nouveau `@Order(10)` — `editionId` vient de passer en CLOSED à l'`@Order(9)` précédent (ex-8) :
    ```java
    @Test
    @Order(10)
    void advance_to_deposit_unblocked_once_other_edition_closed() throws Exception {
        // FR-105 : editionId est Clôturée depuis Order 9 — une troisième édition peut désormais
        // atteindre Dépôt.
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Après Clôture", null, null, null, null, false, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long thirdEditionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(put("/api/admin/editions/" + thirdEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Vêtements", List.of(4))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + thirdEditionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        // Nettoyage impératif : les Order 12/13 suivants font repasser editionId (#1, CLOSED) par
        // POST_SALE via rollback. Ce contrôle (FR-105) ne porte QUE sur la transition
        // Préparation → Dépôt (voir Dev Notes — le rollback CLOSED → POST_SALE n'est pas gardé par
        // cette story) : si cette troisième édition restait en DEPOSIT, le storyboard se
        // retrouverait dans un état à deux éditions actives simultanément, jamais voulu ni
        // spécifié. On la fait donc redescendre et on la supprime pour que la suite du fichier ne
        // dépende jamais de cet état non spécifié.
        mockMvc.perform(post("/api/admin/editions/" + thirdEditionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/editions/" + thirdEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }
    ```

- [x] **Backend — correctif de régression légitime : `EditionManagementIT` (AC 1)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionManagementIT.java` (UPDATE) — `@Order(5)` s'appelle aujourd'hui `admin_create_second_edition_while_active_returns_422` et attend un 422 en créant une deuxième édition pendant que la première est en PREPARATION. Ce comportement est désormais invalide (AC 1). Remplacer par :
    ```java
    @Test
    @Order(5)
    void admin_create_second_edition_while_first_in_preparation_succeeds() throws Exception {
        // FR-010 amendé (Story 2.10) : la Préparation n'est plus exclusive — une deuxième édition
        // peut être créée pendant que la première existe encore en Préparation. Supprimée aussitôt
        // pour que la suite du storyboard (Order 6 attend exactement une édition) reste inchangée ;
        // l'exclusivité Dépôt/Vente/Post-vente (FR-105) elle-même est couverte par PhaseTransitionIT,
        // pas ici.
        EditionDto dto = new EditionDto(null, "Bourse 2027", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null);
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"))
                .andReturn();
        Long secondEditionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(delete("/api/admin/editions/" + secondEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }
    ```
    Le reste du fichier (Order 6 à 28) est inchangé — vérifié ligne à ligne, aucune autre méthode ne dépend du contrôle d'exclusivité à la création.

- [x] **Backend — correctif de régression légitime : `CurrentEditionIT` (AC 4)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/CurrentEditionIT.java` (UPDATE) — `@Order(2)` (`current_edition_returns_200_with_preparation_phase`) suppose que `/api/editions/current` résout une édition en PREPARATION ; ce n'est plus vrai (AC 4). Restructurer en 4 méthodes (`@Order(1)` inchangé) :
    ```java
    @Test
    @Order(2)
    void current_edition_returns_404_while_edition_is_in_preparation() throws Exception {
        // FR-010 amendé (Story 2.10) : PREPARATION n'est plus une phase "active" —
        // /editions/current ne résout rien tant que l'édition n'a pas atteint le Dépôt.
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(get("/api/editions/current").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-active-edition")));
    }

    @Test
    @Order(3)
    void current_edition_returns_200_once_deposit_starts() throws Exception {
        mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("name", "Jouets", "tableNumbers", List.of(1))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/editions/current").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(editionId))
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(4)
    void current_edition_accessible_by_volunteer() throws Exception {
        mockMvc.perform(get("/api/editions/current").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(editionId))
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(5)
    void current_edition_returns_404_after_edition_closed() throws Exception {
        // editionId est déjà en DEPOSIT depuis Order 3 — 2 avancées suffisent pour atteindre POST_SALE
        // (au lieu des 3 avant restructuration, qui partaient de PREPARATION).
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/editions/current").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-active-edition")));
    }
    ```
    Ne pas dupliquer la configuration des catégories dans le nouvel `@Order(5)` : elle est déjà faite à l'`@Order(3)`, et les catégories sont verrouillées (`CategoriesLockedException`) dès que l'édition a quitté la Préparation — une deuxième tentative en DEPOSIT échouerait.

- [x] **Frontend — `CurrentEditionService` : dissocier `ACTIVE_PHASES` de l'enum `ActivePhase` (AC 4)**
  - [x] `pluribourse-frontend/src/app/services/current-edition.service.ts` (UPDATE) — remplacer :
    ```ts
    const ACTIVE_PHASES = new Set<string>(Object.values(ActivePhase));
    ```
    par :
    ```ts
    // Mirrors backend PhaseType.ACTIVE (Story 2.10) : PREPARATION n'est plus "active". Ce set n'est
    // volontairement PAS dérivé de l'enum ActivePhase ci-dessous — celui-ci garde PREPARATION pour
    // ALL_PHASES (ordre du dialogue de contrôle de phase, active-phase.enum.ts), un usage distinct.
    const ACTIVE_PHASES = new Set<string>(['DEPOSIT', 'SALE', 'POST_SALE']);
    ```
    Retirer l'import `ActivePhase` de ce fichier s'il devient inutilisé (c'était sa seule utilisation).
  - **Pourquoi c'est nécessaire, pas cosmétique :** `updateFromEvent()` utilise `ACTIVE_PHASES.has(event.newPhase)` pour décider si un événement SSE `phase-changed` doit vider `currentEdition()`. Sans ce correctif, un retour arrière Dépôt → Préparation laisserait `currentEdition()` non nul avec `phase: 'PREPARATION'` (mise à jour locale optimiste, branche `current.id === event.editionId`), alors qu'un rechargement de page via `loadEdition()` donnerait `null` (404 backend) pour ce même état — incohérence stricte-cache vs état serveur.
  - [x] `pluribourse-frontend/src/app/services/current-edition.service.spec.ts` (UPDATE) — nouveau test dans `describe('updateFromEvent()', ...)`, même patron que le test existant `'sets currentEdition to null when newPhase is CLOSED'` (ligne 89) :
    ```ts
    it('sets currentEdition to null when newPhase is PREPARATION (Story 2.10 — rollback out of the active bucket)', () => {
      service.currentEdition.set(mockEdition);
      service.updateFromEvent({ editionId: 1, newPhase: 'PREPARATION', previousPhase: 'DEPOSIT' });
      expect(service.currentEdition()).toBeNull();
    });
    ```

- [x] **Frontend — `active-phase.enum.ts` : corriger le commentaire devenu inexact (AC 4)**
  - [x] `pluribourse-frontend/src/app/models/active-phase.enum.ts` (UPDATE) — le commentaire `// Mirrors PhaseType.ACTIVE on the backend (org.pluribourse.edition.entity.PhaseType).` (ligne 3) n'est plus vrai depuis la tâche précédente. Remplacer par :
    ```ts
    // Every non-terminal phase, in state-machine order — CLOSED is appended separately below for
    // ALL_PHASES. Despite the name, this no longer mirrors the backend's PhaseType.ACTIVE since
    // Story 2.10 (which excludes PREPARATION) — see CurrentEditionService.ACTIVE_PHASES for that.
    ```

- [x] **Frontend — `edition-form.component.ts` : retirer la branche d'erreur devenue impossible (AC 1)**
  - [x] `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts` (UPDATE) — dans le `catch` de `onSubmit()`, retirer la branche :
    ```ts
    } else if (errorType.endsWith('/edition-already-active')) {
      this.formError.set('edition.create.error.alreadyActive');
    }
    ```
    `createEdition` ne peut plus renvoyer ce type d'erreur (tâche backend ci-dessus) — code mort sinon.
  - [x] `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts` (UPDATE) — supprimer le test `'sets formError key on 422 response (active edition already exists)'` (scénario devenu impossible).

- [x] **Frontend — `phase-control.component.ts` : gérer le nouveau 422 `edition-already-active` sur l'avancement (AC 2)**
  - [x] `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts` (UPDATE) — dans `confirmAdvance()`, ajouter une branche avant le `else` générique, même patron que `no-categories-configured`/`no-volunteer-configured` :
    ```ts
    } else if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/edition-already-active')) {
      this.toast.showError(this.translate.instant('phase.advance.error.editionAlreadyActive'));
    }
    ```
  - [x] `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts` (UPDATE) — ajouter un test miroir de `'confirmAdvance — confirmed: shows specific error toast when no categories are configured'` :
    ```ts
    it('confirmAdvance — confirmed: shows specific error toast when another edition is already active', async () => {
      confirmMock.open.mockReturnValue(of(true));
      editionServiceMock.advancePhase.mockReturnValue(throwError(() => new HttpErrorResponse({
        status: 422,
        error: { type: 'https://pluribourse/errors/edition-already-active' },
      })));
      component.confirmAdvance();
      await fixture.whenStable();
      expect(toastMock.showError).toHaveBeenCalledWith('phase.advance.error.editionAlreadyActive');
    });
    ```

- [x] **i18n — clés FR/EN (AC 1, 2)**
  - [x] `pluribourse-frontend/public/i18n/fr.json` (UPDATE) :
    - Retirer `edition.create.error.alreadyActive` (ligne 162) — devenue orpheline.
    - Ajouter dans `phase.advance.error` (à côté de `noVolunteerConfigured`, ligne ~220) :
      ```json
      "editionAlreadyActive": "Une autre édition est déjà en Dépôt, Vente ou Post-vente. Elle doit être Clôturée avant que celle-ci puisse démarrer le Dépôt."
      ```
  - [x] `pluribourse-frontend/public/i18n/en.json` (UPDATE) — mêmes changements :
    - Retirer `edition.create.error.alreadyActive` (ligne 162).
    - Ajouter dans `phase.advance.error` :
      ```json
      "editionAlreadyActive": "Another edition is already in Deposit, Sale or Post-sale phase. It must reach Closed before this one can start Deposit."
      ```

### Review Findings

- [x] [Review][Defer] Race condition possible sur la garde d'exclusivité Préparation→Dépôt — `EditionService.advancePhase` fait un `existsByPhaseIn(PhaseType.ACTIVE)` (lecture) suivi d'un `save` (écriture) sans verrou explicite ni contrainte d'unicité en base. Deux appels `advancePhase` concurrents visant deux éditions différentes en Préparation pourraient tous les deux passer le contrôle avant que l'un des deux ne committe, aboutissant à deux éditions simultanément en Dépôt — violant la garantie d'exclusivité que FR-105 est censée apporter. [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java:121] (Blind Hunter + Edge Case Hunter, fusionnés) — deferred : un seul admin actif à la fois en pratique, l'avancement de phase est une action manuelle rare et délibérée (pas un flux à haute concurrence comme le scan de caisse) ; risque théorique très faible, à corriger si un incident réel survient (décision utilisateur, revue du 2026-08-25).

- [x] [Review][Defer] Le rollback Clôturé→Post-vente n'est pas gardé par le nouveau contrôle d'exclusivité — un admin pourrait faire revenir une édition Clôturée vers Post-vente pendant qu'une autre édition occupe déjà Dépôt/Vente/Post-vente, recréant la situation à deux éditions actives que FR-105 vise à empêcher. [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java:133] — deferred, pre-existing : déjà documenté et sciemment exclu du périmètre de cette story dans les Dev Notes (« Portée volontairement limitée de FR-105 »), à couvrir par une story dédiée si jugé nécessaire. (Blind Hunter + Edge Case Hunter, fusionnés — confirment indépendamment une décision déjà actée dans la spec)

- [x] [Review][Defer] Commentaire obsolète dans `ItemCatalogIT.java` — décrit encore le catalogue comme limité à « l'édition ACTIVE (PREPARATION/DEPOSIT/SALE/POST_SALE) », inexact depuis le retrait de PREPARATION de `PhaseType.ACTIVE`. [pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java:269] — deferred, pre-existing : fichier non touché par ce diff, n'affecte pas la justesse du test. (Acceptance Auditor)

## Dev Notes

- **Portée volontairement limitée de FR-105 : seule la transition Préparation → Dépôt est gardée, pas le retour arrière Clôturé → Post-vente.** En théorie, un admin pourrait faire revenir en arrière (`rollback`) une édition Clôturée vers Post-vente pendant qu'une *autre* édition occupe déjà Dépôt/Vente/Post-vente — recréant la même situation à deux éditions actives que FR-105 empêche à l'entrée. Le sprint-change-proposal (2026-08-24, point 2) ne mentionne que la transition Préparation → Dépôt ; ni epics.md ni la décision utilisateur ne couvrent ce cas. Décision actée pour cette story, sans besoin de validation utilisateur (lecture directe et littérale de FR-105, aucune ambiguïté sur son périmètre) : ne pas généraliser le contrôle au-delà de ce que FR-105 spécifie explicitement — un rollback bien après clôture nécessitant une manipulation admin délibérée sur deux éditions distinctes est un cas marginal, hors du problème réel motivant ce point du sprint change (préparer la prochaine bourse pendant que la précédente tourne encore). Signalé ici pour trace ; à couvrir dans une story dédiée si l'utilisateur le juge nécessaire après coup.
- **`architecture.md` devient stale sur ce point, volontairement non corrigé par cette story.** § F2 dit encore « Une seule édition active à la fois » (ligne 29) — imprécis depuis FR-010 amendé. Même traitement que les écarts déjà documentés dans les stories précédentes de cet epic (2.7, 2.8) : epics.md/le sprint-change-proposal font foi, pas architecture.md.
- **`/admin/sellers` devient inaccessible pendant que l'édition unique est en Préparation (conséquence de AC 4, pas une régression fonctionnelle).** `activeEditionGuard` (`core/guards/active-edition.guard.ts`) bloque la navigation sans édition active ; `SellerService.getSellers()` (liste) n'a jamais eu de garde de phase mais `search`/`create` exigent déjà DEPOSIT côté serveur (`requireDepositPhase`). Avant cette story, un admin pouvait déjà voir la liste (vide) en Préparation sans pouvoir rien y faire ; après, la page n'est simplement plus atteignable tant que Dépôt n'a pas commencé. Aucune fonctionnalité réelle n'est perdue — décision actée sans besoin de validation (conséquence mécanique de AC 4, cohérente avec le fait que la gestion vendeurs est une opération de Dépôt).
- **Ne pas confondre `ActivePhase` (frontend TS enum) et `PhaseType.ACTIVE` (backend) après cette story — ils divergent désormais intentionnellement.** `ActivePhase` reste `{PREPARATION, DEPOSIT, SALE, POST_SALE}` car `ALL_PHASES` (ordre du dialogue de contrôle de phase, `phase-control.component.ts`) a besoin de PREPARATION comme première étape de la séquence — ce n'est pas la même notion que « phase où une édition compte comme active pour `/editions/current` ». D'où la dissociation explicite de `CurrentEditionService.ACTIVE_PHASES` (tâche dédiée ci-dessus) plutôt qu'un simple retrait de `PREPARATION` dans l'enum partagé, qui aurait cassé `ALL_PHASES`/`nextPhase()`/`prevPhase()`.
- **`EditionListComponent`/`edition-list.component.html` : aucun changement nécessaire, vérifié.** `getAllEditions()` (backend) renvoie déjà toutes les éditions sans filtre de phase (`findAllByOrderByCreatedAtDesc`), et `isEditable()` (frontend) est déjà par ligne (`edition.phase === 'PREPARATION'`) — la coexistence de plusieurs éditions en Préparation dans la liste fonctionne donc déjà correctement sans modification.
- **`EditionCategoryIT`/gestion des catégories (Story 2.5) : aucun changement nécessaire, vérifié.** Le dialogue admin de configuration des catégories (`EditionCategoriesComponent`/`CategoryService.getCategories(editionId)`) cible déjà l'édition par ID explicite (`/admin/editions/{id}/categories`), jamais via « l'édition active » — seul `GET /api/categories` (`CurrentEditionCategoryController`, utilisé par le catalogue bénévole et l'écran de dépôt, tous deux Dépôt+) passe par `getActiveEdition()`, et ces deux usages restent corrects après le changement de `PhaseType.ACTIVE` (déjà DEPOSIT+ dans les deux cas, comportement déjà robuste au cas « aucune édition active » via le même code path).
- **Story 2.3 (blocage bénévole sans édition active) est retirée depuis le 2026-07-06 — aucun impact ici.** Les bénévoles peuvent se connecter à tout moment, indépendamment de la phase ; ce changement ne réintroduit aucune contrainte de connexion.

### Project Structure Notes

- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/PhaseType.java`
- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/exception/EditionAlreadyActiveException.java`
- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java`
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/PhaseTransitionIT.java`
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionManagementIT.java`
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/CurrentEditionIT.java`
- Modifié : `pluribourse-frontend/src/app/services/current-edition.service.ts`
- Modifié : `pluribourse-frontend/src/app/services/current-edition.service.spec.ts`
- Modifié : `pluribourse-frontend/src/app/models/active-phase.enum.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts`
- Modifié : `pluribourse-frontend/public/i18n/fr.json`
- Modifié : `pluribourse-frontend/public/i18n/en.json`
- Aucun nouveau fichier, aucune migration Liquibase (aucun changement de schéma — `PhaseType` reste le même enum de valeurs, seule la liste `ACTIVE` en mémoire change).

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#Point 2 — Phase de planification → Préparation non exclusive] FR-010 amendé, nouveau FR-105, décision validée après contre-proposition utilisateur (retirer PREPARATION de `PhaseType.ACTIVE` plutôt qu'ajouter une nouvelle phase), impact Epic 2 (Story 2.1 : suppression contrôle création ; Story 2.2 : nouveau contrôle sur la transition)
- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#4. Impact MVP et plan d'action] Ordre recommandé : ce point avant le point 1 (devise, Story 2.9) car il touche la même zone Edition — confirmé avec l'utilisateur en `create-story` (2-10 choisie avant 2-9 malgré l'ordre backlog de sprint-status.yaml)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java] `createEdition`/`advancePhase`/`getActiveEdition` — points d'accroche exacts
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/PhaseType.java] `PhaseType.ACTIVE`, seul point de vérité pour la définition d'« édition active »
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/seller/service/SellerService.java] `requireDepositPhase` déjà DEPOSIT-only pour `search`/`create` — confirme que `PhaseType.ACTIVE` incluant PREPARATION n'apportait déjà aucune fonctionnalité réelle en Préparation sur ce module
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionManagementIT.java#Order(5)] Test existant à corriger (régression légitime, comportement inversé par AC 1)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/edition/PhaseTransitionIT.java] Storyboard de référence pour l'insertion des 2 nouveaux scénarios FR-105 et la renumérotation
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/edition/CurrentEditionIT.java#Order(2)] Test existant à restructurer (régression légitime, `/editions/current` ne résout plus PREPARATION)
- [Source: pluribourse-frontend/src/app/services/current-edition.service.ts] `ACTIVE_PHASES`/`updateFromEvent` — la dissociation d'avec `ActivePhase` est nécessaire pour la cohérence cache/serveur après un rollback vers Préparation
- [Source: pluribourse-frontend/src/app/models/active-phase.enum.ts] `ActivePhase`/`ALL_PHASES`/`resolveVolunteerLandingPath` — pourquoi PREPARATION y reste malgré le changement backend
- [Source: pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html] Bannière de phase topbar + section sidebar « Édition active » — masquées tant qu'aucune édition n'est en Dépôt+ (AC 4), vérifié déjà conditionné sur `currentEdition()` existant, aucun changement de template nécessaire
- [Source: pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts] Branche d'erreur `edition-already-active` à retirer (code mort après le changement backend)
- [Source: pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts] `confirmAdvance()` — patron exact à suivre pour la nouvelle branche d'erreur (`no-categories-configured`/`no-volunteer-configured` déjà en place)
- [Source: pluribourse-frontend/src/app/core/guards/active-edition.guard.ts] Confirme que `/admin/sellers` devient inatteignable en Préparation pure — conséquence documentée dans Dev Notes, pas une régression

## Dev Agent Record

### Completion Notes

- Implémentation strictement conforme au code cible fourni dans les Tasks/Subtasks (aucun écart) : `PhaseType.ACTIVE` exclut désormais PREPARATION ; le contrôle d'exclusivité est retiré de `EditionService.createEdition` et déplacé dans `EditionService.advancePhase` (uniquement sur la transition vers DEPOSIT) ; message `EditionAlreadyActiveException` mis à jour.
- `PhaseTransitionIT` : 2 nouveaux scénarios (`advance_to_deposit_blocked_when_another_edition_already_active` @Order(6), `advance_to_deposit_unblocked_once_other_edition_closed` @Order(10)) insérés et les 13 méthodes suivantes renumérotées selon la table de la story — vérifié après coup : `@Order` 1 à 20 sans doublon, storyboard cohérent (données persistantes entre méthodes).
- `EditionManagementIT` @Order(5) et `CurrentEditionIT` @Order(2)-(5) restructurés comme régressions légitimes (comportement inversé par AC 1/AC 4), pas laissés en échec.
- Frontend : `CurrentEditionService.ACTIVE_PHASES` dissocié de l'enum `ActivePhase` (celui-ci garde PREPARATION pour `ALL_PHASES`/le dialogue de contrôle de phase) ; import `ActivePhase` retiré du service (devenu inutilisé) ; branche d'erreur `edition-already-active` retirée de `edition-form.component.ts` (code mort) et ajoutée à `phase-control.component.ts` (nouvelle source du 422) ; clé i18n `edition.create.error.alreadyActive` supprimée (FR/EN), `phase.advance.error.editionAlreadyActive` ajoutée (FR/EN).
- Tests ciblés (`PhaseTransitionIT` 20/20, `EditionManagementIT` 28/28, `CurrentEditionIT` 5/5) et suite frontend complète (670/670) exécutés et verts.
- **3ᵉ impact non anticipé par la story, découvert par la suite backend complète (523 tests, 5 échecs à la première exécution) :** tout endpoint qui résout « l'édition active » via `EditionService.getActiveEdition()` puis vérifie une phase précise (`ItemService.create`, `LotService.create`, `SellerService.search`/`create`, réimpression des étiquettes de dépôt) échouait déjà pendant la Préparation (comme l'affirmait le « Pourquoi c'est sûr » de la première tâche), mais avec un code d'erreur différent : `getActiveEdition()` lève désormais `NoActiveEditionException` (404 `no-active-edition`) **avant** d'atteindre le contrôle de phase spécifique (ex. l'ancien 422 `item-modification-locked`), puisque la Préparation n'est plus « active » (AC 4) — conséquence directe et systémique de AC 4, pas un effet de bord isolé. Vérifié sans impact utilisateur réel : ces écrans (dépôt, vendeurs) sont déjà inatteignables en Préparation pure côté frontend (guards `activeEditionGuard`/`depositPhaseGuard`, cf. Dev Notes), donc ce chemin 404 n'est déclenchable que par appel API direct (comme le fait précisément le test concerné), jamais par la navigation normale ; le gestionnaire frontend spécifique de `item-modification-locked` (`item-form.component.ts`, `lot-form.component.ts`, `deposit-page.component.ts`) reste pleinement fonctionnel pour son cas d'usage réel (une transition de phase pendant que l'écran de dépôt est déjà ouvert, ex. DEPOSIT→SALE), qui ne passe jamais par la Préparation. 5 tests d'intégration corrigés en conséquence (statut + type d'erreur attendus, avec commentaire explicatif) : `ItemManagementIT.create_item_outside_deposit_phase_is_blocked`, `LotManagementIT.create_lot_outside_deposit_phase_is_blocked`, `ThermalLabelPrintingIT.reprint_labels_outside_deposit_or_post_sale_phase_is_blocked`, `SellerManagementIT.search_during_preparation_phase_is_blocked` et `.create_during_preparation_phase_is_blocked`. Suite backend complète re-exécutée après correctif : 523/523, aucune régression.

## File List

- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/PhaseType.java`
- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/exception/EditionAlreadyActiveException.java`
- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java`
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/PhaseTransitionIT.java`
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionManagementIT.java`
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/CurrentEditionIT.java`
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemManagementIT.java` (régression légitime découverte par la suite complète — voir Completion Notes)
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java` (idem)
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java` (idem)
- Modifié : `pluribourse-backend/src/test/java/org/pluribourse/domain/seller/SellerManagementIT.java` (idem)
- Modifié : `pluribourse-frontend/src/app/services/current-edition.service.ts`
- Modifié : `pluribourse-frontend/src/app/services/current-edition.service.spec.ts`
- Modifié : `pluribourse-frontend/src/app/models/active-phase.enum.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts`
- Modifié : `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts`
- Modifié : `pluribourse-frontend/public/i18n/fr.json`
- Modifié : `pluribourse-frontend/public/i18n/en.json`
- Aucun nouveau fichier, aucune migration Liquibase.

## Change Log

- 2026-08-24 — Story créée via `bmad-create-story`. Point d'entrée choisi après clarification explicite avec l'utilisateur : sprint-status.yaml liste 2-9 (devise) avant 2-10 (préparation non exclusive) par ordre d'ajout au backlog, mais le sprint-change-proposal du même jour recommande l'ordre inverse (2-10 avant 2-9, même zone `Edition`, 2-10 modifie la machine d'état de base) — l'utilisateur a confirmé suivre cette recommandation. Analyse exhaustive du code existant ayant révélé 2 impacts non anticipés par le sprint-change-proposal (qui ne vérifiait que les appelants de `getActiveEdition()` côté service) : (1) `/api/editions/current` — et donc toute la notion frontend d'« édition courante » (bannière topbar, section sidebar admin, `CurrentEditionService.updateFromEvent`) — cesse de résoudre une édition en Préparation, avec un test existant (`CurrentEditionIT`) qui l'affirmait explicitement et devait être restructuré ; (2) l'enum frontend `ActivePhase` ne peut pas simplement perdre `PREPARATION` en miroir du backend car `ALL_PHASES` (ordre du dialogue de contrôle de phase) en a besoin pour un usage différent — d'où la dissociation explicite de `CurrentEditionService.ACTIVE_PHASES`. Un troisième impact (`/admin/sellers` inatteignable en Préparation pure) et un gap de portée volontairement non traité (rollback Clôturé → Post-vente non gardé par FR-105) sont documentés en Dev Notes comme décisions actées sans validation utilisateur nécessaire (lecture directe, non-régression fonctionnelle réelle) ou comme limite de portée assumée face au texte exact de FR-105. 2 tests d'intégration existants identifiés comme régressions légitimes à corriger explicitement (`EditionManagementIT` Order 5, `CurrentEditionIT` Order 2-4) plutôt que de les laisser échouer en aveugle pendant `dev-story`. Statut → ready-for-dev.
- 2026-08-25 — Implémentation (`dev-story`) : code cible suivi tel quel (aucun écart avec les Tasks/Subtasks). Un 3ᵉ impact non anticipé par la story a été découvert par la suite backend complète (523 tests, 5 échecs à la première exécution) : `ItemService.create`, `LotService.create`, `SellerService.search`/`create` et la réimpression des étiquettes de dépôt échouaient déjà pendant la Préparation, mais désormais avec 404 `no-active-edition` (via `EditionService.getActiveEdition()`) au lieu de l'ancien 422 spécifique (`item-modification-locked`/`seller-management-locked`/`deposit-reprint-not-allowed`) — conséquence directe et systémique de AC 4, sans impact utilisateur réel (chemins déjà bloqués côté frontend par `activeEditionGuard`/`depositPhaseGuard` en Préparation pure). 5 tests corrigés en conséquence (voir Dev Agent Record). Suite backend complète (523/523) et suite frontend complète (670/670) vertes après correctif, aucune régression. Statut → review.
- 2026-08-25 — Revue de code (`bmad-code-review`, Blind Hunter + Edge Case Hunter + Acceptance Auditor, sur diff 27bc97f→HEAD incluant un commit intermédiaire hors session `61cf9b1`). Acceptance Auditor confirme les 5 AC satisfaites par le code (pas seulement les tests), vérification exhaustive des appelants de `getActiveEdition()`. 1 decision-needed résolue en defer (voir deferred-work.md) : race condition théorique sur la garde d'exclusivité `advancePhase` (`existsByPhaseIn` puis `save` sans verrou) — risque jugé très faible (avancement de phase = action manuelle rare, un seul admin actif en pratique). 2 items différés supplémentaires (rollback Clôturé→Post-vente non gardé — déjà documenté et sciemment exclu du périmètre par la story elle-même ; commentaire obsolète dans `ItemCatalogIT.java`, fichier hors diff). 10 findings rejetés comme bruit après vérification (nom d'exception imprécis mais slug volontairement conservé, absence de plafond sur les éditions en Préparation conforme à AC1, couplage par égalité de phase cohérent avec le reste du code, "3ᵉ impact" 404 déjà validé sans risque réel, double source de vérité `ActivePhase`/`ACTIVE_PHASES` déjà actée comme divergence intentionnelle, etc.). Aucun patch de code nécessaire. Statut → done.
