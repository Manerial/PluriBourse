package org.pluribourse.item;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.pluribourse.edition.dto.EditionCategoryDto;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.item.dto.CreateLotDto;
import org.pluribourse.item.dto.CreateLotItemDto;
import org.pluribourse.item.dto.ItemDto;
import org.pluribourse.item.dto.LotDto;
import org.pluribourse.seller.dto.SellerDto;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Categories are set up with overlapping tables (Jouets=[1,2], Livres=[2,3]) so a lot spanning
 * both categories exercises the "least loaded table across all categories" branch of FR-023
 * (Story 3.2), applied independently per lot item.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LotManagementIT extends IntegrationTest {

    @Autowired private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;
    private Long jouetsCategoryId;
    private Long livresCategoryId;
    private Long foreignCategoryId;
    private Long sellerAId;
    private Long createdLotId;

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
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Lots 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)))))
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
                categoriesResult.getResponse().getContentAsString(), new TypeReference<>() {});
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
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Fermee 2025", null, null, null, null, false, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3)))))
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
                categoriesResult.getResponse().getContentAsString(), new TypeReference<List<EditionCategoryDto>>() {}).get(0).id();

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/admin/editions/" + otherEditionId + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk());
        }
        return otherCategoryId;
    }

    @Test @Order(1)
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
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }

    @Test @Order(2)
    void advance_edition_to_deposit_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test @Order(3)
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

    @Test @Order(4)
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

    @Test @Order(5)
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

    @Test @Order(6)
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
        assertThat(created.name()).isEqualTo("Lot Jouets et Livres");
        assertThat(created.globalPrice()).isEqualByComparingTo("25.00");
        assertThat(created.items()).hasSize(2);
        assertThat(created.items()).extracting(ItemDto::price).containsOnlyNulls();
        assertThat(created.items()).extracting(ItemDto::tableNumber).containsExactly(1, 2);
        assertThat(created.items().get(1).incomplete()).isTrue();
        assertThat(created.items().get(1).comment()).isEqualTo("Couverture abimee");
    }

    @Test @Order(7)
    void get_items_by_seller_returns_lot_items_with_lot_fields_and_null_price() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> items = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(items).hasSize(2);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.price()).isNull();
            assertThat(item.lotId()).isEqualTo(createdLotId);
            assertThat(item.lotName()).isEqualTo("Lot Jouets et Livres");
            assertThat(item.lotPrice()).isEqualByComparingTo("25.00");
        });
    }

    @Test @Order(8)
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

    @Test @Order(9)
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

    @Test @Order(10)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test @Order(11)
    void create_lot_outside_deposit_phase_is_blocked_again() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, "Lot Tardif", new BigDecimal("9.00"), List.of(
                new CreateLotItemDto(jouetsCategoryId, "Piece A", false, null),
                new CreateLotItemDto(jouetsCategoryId, "Piece B", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }
}
