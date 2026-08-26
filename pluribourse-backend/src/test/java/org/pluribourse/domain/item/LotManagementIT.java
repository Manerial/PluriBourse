package org.pluribourse.domain.item;

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.seller.dto.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.math.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Categories are set up with overlapping tables (Jouets=[1,2], Livres=[2,3]) so a lot's category
 * change from Jouets to Livres exercises the "least loaded table across all categories" branch of
 * FR-023 (Story 3.2) — a lot has a single shared category (this story), so the whole lot is
 * reassigned to one new shared table in a single operation, never per item.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LotManagementIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;
    private Long jouetsCategoryId;
    private Long livresCategoryId;
    private Long foreignCategoryId;
    private Long sellerAId;
    private Long createdLotId;
    private Long itemKaplaId;
    private Long itemTintinId;
    private Long itemPlaymobilId;
    private Long itemBarbieId;
    private Long secondLotId;
    private Long secondLotItemAId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        // A fully closed "other" edition, created and retired before the real test edition, purely
        // to obtain a valid categoryId that belongs to a different edition (foreignCategoryId) —
        // exercises the edition-ownership check in EditionScopedLookup.findCategoryInEdition,
        // distinct from a categoryId that does not exist at all.
        foreignCategoryId = createClosedEditionWithOneCategory();

        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Lots 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        List<EditionCategoryDto> categoriesPayload = List.of(
                new EditionCategoryDto(null, "Jouets", List.of(1, 2)),
                new EditionCategoryDto(null, "Livres", List.of(2, 3))
        );
        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoriesPayload)))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> categories = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(), new TypeReference<>() {
                });
        jouetsCategoryId = categories.get(0).id();
        livresCategoryId = categories.get(1).id();

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
    }

    private Long createClosedEditionWithOneCategory() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Fermee 2025", null, null, null, null, false, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long otherEditionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + otherEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Categorie Autre Edition", List.of(1))))))
                .andExpect(status().isOk())
                .andReturn();
        Long otherCategoryId = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(), new TypeReference<List<EditionCategoryDto>>() {
                }).getFirst().id();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/admin/editions/" + otherEditionId + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk());
        }
        // POST_SALE -> CLOSED only via the dedicated /close endpoint (FR-096 follow-up fix).
        mockMvc.perform(post("/api/admin/editions/" + otherEditionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk());
        return otherCategoryId;
    }

    @Test
    @Order(1)
    void create_lot_outside_deposit_phase_is_blocked() throws Exception {
        // No seller can exist yet at this point (SellerService also requires the DEPOSIT phase),
        // so sellerProfileId is necessarily a made-up id.
        // Story 2.10 : editionId est encore en PREPARATION ici — PhaseType.ACTIVE ne la couvre plus
        // (AC 4), donc LotService.create() échoue désormais dès editionService.getActiveEdition()
        // (404 no-active-edition), avant même d'atteindre la résolution du vendeur ou le contrôle de
        // phase (l'ancien 422 item-modification-locked). Le résultat métier reste identique (création
        // bloquée).
        CreateLotDto payload = new CreateLotDto(1L, jouetsCategoryId, "Lot Legos", new BigDecimal("15.00"), List.of(
                new CreateLotItemDto("Piece A", false, null),
                new CreateLotItemDto("Piece B", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-active-edition")));
    }

    @Test
    @Order(2)
    void advance_edition_to_deposit_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(3)
    void create_seller() throws Exception {
        SellerDto payload = new SellerDto(null, "Alice", "Vendeuse", "alice.lots@email.com", "0600000000");
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        sellerAId = objectMapper.readValue(result.getResponse().getContentAsString(), SellerDto.class).id();
    }

    @Test
    @Order(4)
    void create_lot_with_a_single_item_is_rejected() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, jouetsCategoryId, "Lot incomplet", new BigDecimal("10.00"), List.of(
                new CreateLotItemDto("Piece unique", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void create_lot_with_a_blank_item_name_is_rejected() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, jouetsCategoryId, "Lot Invalide", new BigDecimal("10.00"), List.of(
                new CreateLotItemDto("Piece valide", false, null),
                new CreateLotItemDto("  ", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void create_lot_with_two_items_shares_a_single_table() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, jouetsCategoryId, "Lot Jouets", new BigDecimal("25.00"), List.of(
                new CreateLotItemDto("Kapla", false, null),
                new CreateLotItemDto("BD Tintin", true, "Couverture abimee")
        ));
        MvcResult result = mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        LotDto created = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        createdLotId = created.id();
        itemKaplaId = created.items().get(0).id();
        itemTintinId = created.items().get(1).id();
        assertThat(created.name()).isEqualTo("Lot Jouets");
        assertThat(created.globalPrice()).isEqualByComparingTo("25.00");
        assertThat(created.categoryId()).isEqualTo(jouetsCategoryId);
        assertThat(created.categoryName()).isEqualTo("Jouets");
        assertThat(created.items()).hasSize(2);
        assertThat(created.items()).extracting(ItemDto::price).containsOnlyNulls();
        // A lot now has ONE category, so both members share the SAME table — replaces the old
        // "different categories in the same lot get different tables" scenario, which no longer
        // has an equivalent (FR-023 precise, AC 2).
        assertThat(created.items()).extracting(ItemDto::tableNumber).containsExactly(1, 1);
        assertThat(created.items().get(1).incomplete()).isTrue();
        assertThat(created.items().get(1).comment()).isEqualTo("Couverture abimee");
    }

    @Test
    @Order(7)
    void get_items_by_seller_returns_lot_items_with_lot_and_category_fields_and_null_price() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> items = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(items).hasSize(2);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.price()).isNull();
            assertThat(item.lotId()).isEqualTo(createdLotId);
            assertThat(item.lotName()).isEqualTo("Lot Jouets");
            assertThat(item.lotPrice()).isEqualByComparingTo("25.00");
            // The lot's single category is still copied onto every member (AC 5) — Item.category
            // stays populated per member even though the form no longer lets it be chosen
            // independently (see story Dev Notes § Décision de conception).
            assertThat(item.categoryId()).isEqualTo(jouetsCategoryId);
            assertThat(item.categoryName()).isEqualTo("Jouets");
        });
    }

    @Test
    @Order(8)
    void create_lot_with_unknown_category_returns_404() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, 999999L, "Lot Invalide", new BigDecimal("12.00"), List.of(
                new CreateLotItemDto("Piece valide", false, null),
                new CreateLotItemDto("Piece invalide", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/category-not-found")));
    }

    @Test
    @Order(9)
    void create_lot_with_category_from_another_edition_returns_404() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, foreignCategoryId, "Lot Invalide", new BigDecimal("12.00"), List.of(
                new CreateLotItemDto("Piece valide", false, null),
                new CreateLotItemDto("Piece autre edition", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/category-not-found")));
    }

    @Test
    @Order(10)
    void update_lot_name_and_price_with_unchanged_category_triggers_no_table_reassignment() throws Exception {
        UpdateLotDto payload = new UpdateLotDto(jouetsCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, "BD Tintin", true, "Couverture abimee")
        ));
        MvcResult result = mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        LotDto updated = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        assertThat(updated.name()).isEqualTo("Lot Jouets Modifie");
        assertThat(updated.globalPrice()).isEqualByComparingTo("30.00");
        assertThat(updated.items()).hasSize(2);
        // Category unchanged (still Jouets) — no reassignment, both members keep their original table.
        assertThat(updated.items()).extracting(ItemDto::tableNumber).containsExactly(1, 1);

        MvcResult itemsResult = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> items = objectMapper.readValue(itemsResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(items).filteredOn(item -> item.lotId().equals(createdLotId))
                .allSatisfy(item -> {
                    assertThat(item.lotName()).isEqualTo("Lot Jouets Modifie");
                    assertThat(item.lotPrice()).isEqualByComparingTo("30.00");
                });
    }

    @Test
    @Order(11)
    void create_second_lot_for_cross_lot_and_deletion_tests() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, jouetsCategoryId, "Lot a Supprimer", new BigDecimal("5.00"), List.of(
                new CreateLotItemDto("Piece Jetable A", false, null),
                new CreateLotItemDto("Piece Jetable B", false, null)
        ));
        MvcResult result = mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        LotDto created = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        secondLotId = created.id();
        secondLotItemAId = created.items().get(0).id();
    }

    @Test
    @Order(12)
    void update_lot_add_item_without_changing_category_lands_on_the_lots_existing_table() throws Exception {
        UpdateLotDto payload = new UpdateLotDto(jouetsCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, "BD Tintin", true, "Couverture abimee"),
                new UpdateLotItemDto(null, "Playmobil", false, null)
        ));
        MvcResult result = mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        LotDto updated = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        assertThat(updated.items()).hasSize(3);
        ItemDto playmobil = updated.items().stream().filter(i -> i.name().equals("Playmobil")).findFirst().orElseThrow();
        itemPlaymobilId = playmobil.id();
        // Category unchanged (Jouets): the lot's already-assigned table (1) is found directly via
        // the seller's existing members in that category — no new least-loaded computation.
        assertThat(playmobil.tableNumber()).isEqualTo(1);
        assertThat(playmobil.categoryId()).isEqualTo(jouetsCategoryId);
        assertThat(playmobil.price()).isNull();
    }

    @Test
    @Order(13)
    void update_lot_changes_category_reassigns_every_member_to_one_new_shared_table() throws Exception {
        // Exercises the categoryChanged branch and the multi-id excludeItemIds path together: every
        // current member (Kapla, Tintin, Playmobil) AND a brand-new item (Barbie) submitted in the
        // same request, all landing on a single freshly-computed Livres table.
        UpdateLotDto payload = new UpdateLotDto(livresCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, "BD Tintin", true, "Couverture abimee"),
                new UpdateLotItemDto(itemPlaymobilId, "Playmobil", false, null),
                new UpdateLotItemDto(null, "Barbie", false, null)
        ));
        MvcResult result = mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        LotDto updated = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        assertThat(updated.items()).hasSize(4);
        assertThat(updated.categoryId()).isEqualTo(livresCategoryId);
        assertThat(updated.categoryName()).isEqualTo("Livres");
        // Existing members' ids must be preserved (same rows updated, not deleted+recreated).
        assertThat(updated.items()).extracting(ItemDto::id).contains(itemKaplaId, itemTintinId, itemPlaymobilId);
        ItemDto barbie = updated.items().stream().filter(i -> i.name().equals("Barbie")).findFirst().orElseThrow();
        itemBarbieId = barbie.id();
        // No seller item was already on a Livres table (all were on Jouets' table 1) — the recount
        // excludes the lot's own remaining members, so it lands on the least-loaded Livres table,
        // shared by every member of the lot, old and new alike.
        assertThat(updated.items()).extracting(ItemDto::tableNumber).containsOnly(2);
        assertThat(updated.items()).extracting(ItemDto::categoryId).containsOnly(livresCategoryId);
    }

    @Test
    @Order(14)
    void update_lot_with_item_id_not_belonging_to_lot_returns_404() throws Exception {
        UpdateLotDto unknownIdPayload = new UpdateLotDto(livresCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(999999999L, "Fantome", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownIdPayload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-not-found")));

        // A real, existing item id is just as invalid here if it belongs to a *different* lot —
        // membership is scoped per-lot, not merely "does this id exist anywhere".
        UpdateLotDto otherLotItemPayload = new UpdateLotDto(livresCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(secondLotItemAId, "Piece Jetable A", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherLotItemPayload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-not-found")));
    }

    @Test
    @Order(15)
    void update_lot_remove_item_succeeds() throws Exception {
        UpdateLotDto payload = new UpdateLotDto(livresCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, "BD Tintin", true, "Couverture abimee"),
                new UpdateLotItemDto(itemBarbieId, "Barbie", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        MvcResult afterResult = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> after = objectMapper.readValue(afterResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(after).extracting(ItemDto::id).doesNotContain(itemPlaymobilId);
    }

    @Test
    @Order(16)
    void update_lot_to_single_item_is_rejected() throws Exception {
        UpdateLotDto payload = new UpdateLotDto(livresCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(17)
    void update_unknown_lot_returns_404() throws Exception {
        UpdateLotDto payload = new UpdateLotDto(jouetsCategoryId, "Lot Fantome", new BigDecimal("10.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, "BD Tintin", false, null)
        ));
        mockMvc.perform(put("/api/lots/999999999")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/lot-not-found")));
    }

    @Test
    @Order(18)
    void update_lot_with_duplicate_item_id_is_rejected() throws Exception {
        UpdateLotDto payload = new UpdateLotDto(livresCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, "Kapla", false, null),
                new UpdateLotItemDto(itemKaplaId, "Kapla en double", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/duplicate-lot-item-id")));
    }

    @Test
    @Order(19)
    void delete_unknown_lot_returns_404() throws Exception {
        mockMvc.perform(delete("/api/lots/999999999")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/lot-not-found")));
    }

    @Test
    @Order(20)
    void delete_lot_removes_lot_and_all_member_items() throws Exception {
        MvcResult beforeResult = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> before = objectMapper.readValue(beforeResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        List<Long> deletedIds = before.stream().filter(i -> secondLotId.equals(i.lotId())).map(ItemDto::id).toList();
        assertThat(deletedIds).hasSize(2);

        mockMvc.perform(delete("/api/lots/" + secondLotId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNoContent());

        MvcResult afterResult = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> after = objectMapper.readValue(afterResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(after).extracting(ItemDto::id).doesNotContainAnyElementsOf(deletedIds);
    }

    @Test
    @Order(21)
    void update_item_belonging_to_a_lot_via_the_individual_item_endpoint_is_rejected() throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerAId, jouetsCategoryId, "Kapla", new BigDecimal("5.00"), false, null);
        mockMvc.perform(put("/api/items/" + itemKaplaId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-belongs-to-lot")));
    }

    @Test
    @Order(22)
    void delete_item_from_lot_with_more_than_two_members_succeeds() throws Exception {
        mockMvc.perform(delete("/api/items/" + itemKaplaId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNoContent());

        MvcResult afterResult = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> after = objectMapper.readValue(afterResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(after).extracting(ItemDto::id).doesNotContain(itemKaplaId);
        assertThat(after).filteredOn(item -> createdLotId.equals(item.lotId())).hasSize(2);
    }

    @Test
    @Order(23)
    void delete_item_from_lot_with_exactly_two_members_is_rejected() throws Exception {
        mockMvc.perform(delete("/api/items/" + itemTintinId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/lot-below-minimum-members")));
    }

    @Test
    @Order(24)
    void update_lot_category_with_only_new_items_succeeds() throws Exception {
        // Regresses TableAssignmentService.assignTable: when every submitted item is new,
        // remainingMemberIds is empty — must still resolve as "exclude nothing", not an
        // empty JPQL NOT IN list (see Review Findings).
        UpdateLotDto payload = new UpdateLotDto(jouetsCategoryId, "Lot Jouets Renouvele", new BigDecimal("18.00"), List.of(
                new UpdateLotItemDto(null, "Duplo", false, null),
                new UpdateLotItemDto(null, "Puzzle", false, null)
        ));
        MvcResult result = mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        LotDto updated = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        assertThat(updated.categoryId()).isEqualTo(jouetsCategoryId);
        assertThat(updated.items()).hasSize(2);
        assertThat(updated.items()).extracting(ItemDto::tableNumber).doesNotContainNull();
        assertThat(updated.items()).extracting(ItemDto::tableNumber).containsOnly(updated.items().get(0).tableNumber());
    }

    @Test
    @Order(25)
    void patch_completeness_of_an_item_belonging_to_a_lot_still_succeeds() throws Exception {
        // Dev Notes § Hors périmètre exempts PATCH from the AC 6 guard (it never touches
        // category/tableNumber) — proves that exemption still behaves correctly post-story rather
        // than only asserting it by reasoning.
        MvcResult beforeResult = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> before = objectMapper.readValue(beforeResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        Long lotMemberId = before.stream().filter(i -> createdLotId.equals(i.lotId())).findFirst().orElseThrow().id();

        ItemCompletenessDto payload = new ItemCompletenessDto(true, "Piece manquante");
        mockMvc.perform(patch("/api/items/" + lotMemberId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomplete").value(true))
                .andExpect(jsonPath("$.comment").value("Piece manquante"))
                .andExpect(jsonPath("$.lotId").value(createdLotId));
    }

    @Test
    @Order(26)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(27)
    void update_lot_outside_deposit_phase_is_blocked() throws Exception {
        UpdateLotDto payload = new UpdateLotDto(livresCategoryId, "Lot Jouets Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(null, "Duplo", false, null),
                new UpdateLotItemDto(null, "Puzzle", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }

    @Test
    @Order(28)
    void delete_lot_outside_deposit_phase_is_blocked() throws Exception {
        mockMvc.perform(delete("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }

    @Test
    @Order(29)
    void create_lot_outside_deposit_phase_is_blocked_again() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, jouetsCategoryId, "Lot Tardif", new BigDecimal("9.00"), List.of(
                new CreateLotItemDto("Piece A", false, null),
                new CreateLotItemDto("Piece B", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }
}
