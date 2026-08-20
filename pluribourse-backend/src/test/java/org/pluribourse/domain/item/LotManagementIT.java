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
 * Categories are set up with overlapping tables (Jouets=[1,2], Livres=[2,3]) so a lot spanning
 * both categories exercises the "least loaded table across all categories" branch of FR-023
 * (Story 3.2), applied independently per lot item.
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
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Lots 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
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
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Fermee 2025", null, null, null, null, false, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3), null))))
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

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/admin/editions/" + otherEditionId + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk());
        }
        return otherCategoryId;
    }

    @Test
    @Order(1)
    void create_lot_outside_deposit_phase_is_blocked() throws Exception {
        // No seller can exist yet at this point (SellerService also requires the DEPOSIT phase),
        // so sellerProfileId is necessarily a made-up id. This test relies on LotService checking
        // the phase before resolving the seller (same order as ItemService) — if that ordering ever
        // flips, this would 404 (seller not found) instead of 422 (phase locked) and fail loudly.
        CreateLotDto payload = new CreateLotDto(1L, "Lot Legos", new BigDecimal("15.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece A", false, null),
                new CreateLotItemDto(jouetsCategoryId, "Piece B", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
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
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot incomplet", new BigDecimal("10.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece unique", false, null)
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
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot Invalide", new BigDecimal("10.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece valide", false, null),
                new CreateLotItemDto(jouetsCategoryId, "  ", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void create_lot_with_two_items_assigns_a_table_per_item() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot Jouets et Livres", new BigDecimal("25.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Kapla", false, null),
                new CreateLotItemDto(livresCategoryId, "BD Tintin", true, "Couverture abimee")
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
        assertThat(created.name()).isEqualTo("Lot Jouets et Livres");
        assertThat(created.globalPrice()).isEqualByComparingTo("25.00");
        assertThat(created.items()).hasSize(2);
        assertThat(created.items()).extracting(ItemDto::price).containsOnlyNulls();
        assertThat(created.items()).extracting(ItemDto::tableNumber).containsExactly(1, 2);
        assertThat(created.items().get(1).incomplete()).isTrue();
        assertThat(created.items().get(1).comment()).isEqualTo("Couverture abimee");
    }

    @Test
    @Order(7)
    void get_items_by_seller_returns_lot_items_with_lot_fields_and_null_price() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> items = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(items).hasSize(2);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.price()).isNull();
            assertThat(item.lotId()).isEqualTo(createdLotId);
            assertThat(item.lotName()).isEqualTo("Lot Jouets et Livres");
            assertThat(item.lotPrice()).isEqualByComparingTo("25.00");
        });
    }

    @Test
    @Order(8)
    void create_lot_with_unknown_category_returns_404() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot Invalide", new BigDecimal("12.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece valide", false, null),
                new CreateLotItemDto(999999L, "Piece invalide", false, null)
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
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot Invalide", new BigDecimal("12.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece valide", false, null),
                new CreateLotItemDto(foreignCategoryId, "Piece autre edition", false, null)
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
    void update_lot_name_and_price_reflected_on_all_member_items() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, livresCategoryId, "BD Tintin", true, "Couverture abimee")
        ));
        MvcResult result = mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        LotDto updated = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        assertThat(updated.name()).isEqualTo("Lot Jouets et Livres Modifie");
        assertThat(updated.globalPrice()).isEqualByComparingTo("30.00");
        assertThat(updated.items()).hasSize(2);

        MvcResult itemsResult = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> items = objectMapper.readValue(itemsResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(items).filteredOn(item -> item.lotId().equals(createdLotId))
                .allSatisfy(item -> {
                    assertThat(item.lotName()).isEqualTo("Lot Jouets et Livres Modifie");
                    assertThat(item.lotPrice()).isEqualByComparingTo("30.00");
                });
    }

    @Test
    @Order(11)
    void create_second_lot_for_cross_lot_and_deletion_tests() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot a Supprimer", new BigDecimal("5.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece Jetable A", false, null),
                new CreateLotItemDto(jouetsCategoryId, "Piece Jetable B", false, null)
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
    void update_lot_add_item_assigns_seller_existing_table_for_that_category() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, livresCategoryId, "BD Tintin", true, "Couverture abimee"),
                new UpdateLotItemDto(null, jouetsCategoryId, "Playmobil", false, null)
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
        // Seller already has a Jouets table (Kapla, table 1) — FR-023 keeps a single table per
        // category per seller, so the new item must land on that same table, not a freshly
        // computed least-loaded one.
        assertThat(playmobil.tableNumber()).isEqualTo(1);
        assertThat(playmobil.price()).isNull();
    }

    @Test
    @Order(13)
    void update_lot_change_item_category_reassigns_table() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, jouetsCategoryId, "BD Tintin", true, "Couverture abimee"),
                new UpdateLotItemDto(itemPlaymobilId, jouetsCategoryId, "Playmobil", false, null)
        ));
        MvcResult result = mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        LotDto updated = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        assertThat(updated.items()).hasSize(3);
        // Playmobil's id must be preserved (same row updated, not deleted+recreated) now that the
        // payload correctly carries its captured id instead of null.
        assertThat(updated.items()).extracting(ItemDto::id).contains(itemPlaymobilId);
        ItemDto tintin = updated.items().stream().filter(i -> i.id().equals(itemTintinId)).findFirst().orElseThrow();
        assertThat(tintin.categoryId()).isEqualTo(jouetsCategoryId);
        // Reassigned to the seller's existing Jouets table, same rationale as the previous test.
        assertThat(tintin.tableNumber()).isEqualTo(1);
    }

    @Test
    @Order(14)
    void update_lot_reassigns_category_and_adds_item_in_the_same_request() throws Exception {
        // Exercises the combined lockOrder path the Dev Notes call out explicitly: an existing
        // member's category reassignment (Tintin: Jouets -> Livres) and a brand-new item (Barbie)
        // processed together, sorted by ascending category id in the same PUT.
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, livresCategoryId, "BD Tintin", true, "Couverture abimee"),
                new UpdateLotItemDto(itemPlaymobilId, jouetsCategoryId, "Playmobil", false, null),
                new UpdateLotItemDto(null, jouetsCategoryId, "Barbie", false, null)
        ));
        MvcResult result = mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        LotDto updated = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);
        assertThat(updated.items()).hasSize(4);
        ItemDto tintin = updated.items().stream().filter(i -> i.id().equals(itemTintinId)).findFirst().orElseThrow();
        assertThat(tintin.categoryId()).isEqualTo(livresCategoryId);
        // No seller item remains in Livres at this point (Tintin just left it), so this lands back
        // on the freshly computed least-loaded Livres table rather than a pre-existing one.
        assertThat(tintin.tableNumber()).isEqualTo(2);
        ItemDto barbie = updated.items().stream().filter(i -> i.name().equals("Barbie")).findFirst().orElseThrow();
        itemBarbieId = barbie.id();
        // Seller already has a Jouets table (Kapla/Playmobil, table 1) — same shortcut as the
        // earlier "add item" test.
        assertThat(barbie.tableNumber()).isEqualTo(1);
        assertThat(barbie.price()).isNull();
    }

    @Test
    @Order(15)
    void update_lot_with_item_id_not_belonging_to_lot_returns_404() throws Exception {
        UpdateLotDto unknownIdPayload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(999999999L, jouetsCategoryId, "Fantome", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownIdPayload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-not-found")));

        // A real, existing item id is just as invalid here if it belongs to a *different* lot —
        // membership is scoped per-lot, not merely "does this id exist anywhere".
        UpdateLotDto otherLotItemPayload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(secondLotItemAId, jouetsCategoryId, "Piece Jetable A", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherLotItemPayload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-not-found")));
    }

    @Test
    @Order(16)
    void update_lot_remove_item_succeeds() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, livresCategoryId, "BD Tintin", true, "Couverture abimee"),
                new UpdateLotItemDto(itemBarbieId, jouetsCategoryId, "Barbie", false, null)
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
    @Order(17)
    void update_lot_to_single_item_is_rejected() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(18)
    void update_unknown_lot_returns_404() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Fantome", new BigDecimal("10.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, jouetsCategoryId, "BD Tintin", false, null)
        ));
        mockMvc.perform(put("/api/lots/999999999")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/lot-not-found")));
    }

    @Test
    @Order(19)
    void update_lot_with_duplicate_item_id_is_rejected() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla en double", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/duplicate-lot-item-id")));
    }

    @Test
    @Order(20)
    void delete_unknown_lot_returns_404() throws Exception {
        mockMvc.perform(delete("/api/lots/999999999")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/lot-not-found")));
    }

    @Test
    @Order(21)
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
    @Order(22)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(23)
    void update_lot_outside_deposit_phase_is_blocked() throws Exception {
        UpdateLotDto payload = new UpdateLotDto("Lot Jouets et Livres Modifie", new BigDecimal("30.00"), List.of(
                new UpdateLotItemDto(itemKaplaId, jouetsCategoryId, "Kapla", false, null),
                new UpdateLotItemDto(itemTintinId, jouetsCategoryId, "BD Tintin", false, null)
        ));
        mockMvc.perform(put("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }

    @Test
    @Order(24)
    void delete_lot_outside_deposit_phase_is_blocked() throws Exception {
        mockMvc.perform(delete("/api/lots/" + createdLotId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }

    @Test
    @Order(25)
    void create_lot_outside_deposit_phase_is_blocked_again() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot Tardif", new BigDecimal("9.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece A", false, null),
                new CreateLotItemDto(jouetsCategoryId, "Piece B", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }
}
