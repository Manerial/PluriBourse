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
 * Categories are set up with overlapping tables (Jouets=[1,2], Livres=[2,3]) so the
 * "least loaded table across all categories" branch of FR-023 is actually exercised,
 * not just the per-category count.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ItemManagementIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private MockHttpSession sellerSession;
    private Long editionId;
    private Long jouetsCategoryId;
    private Long livresCategoryId;
    private Long sellerAId;
    private Long sellerBId;
    private Long sellerCId;
    private Long itemKaplaId;
    private Long itemPuzzleId;
    private Long itemVeloId;
    private Long itemBdId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Items 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
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

        MvcResult sellerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "seller1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        sellerSession = (MockHttpSession) sellerLogin.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void create_item_outside_deposit_phase_is_blocked() throws Exception {
        CreateItemDto payload = new CreateItemDto(1L, jouetsCategoryId, "Kapla", new BigDecimal("5.00"), false, null);
        mockMvc.perform(post("/api/items")
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
    void create_sellers() throws Exception {
        sellerAId = createSeller("Alice", "Vendeuse", "alice.vendeuse@email.com");
        sellerBId = createSeller("Bruno", "Vendeur", "bruno.vendeur@email.com");
        sellerCId = createSeller("Chloe", "Vendeuse", "chloe.vendeuse@email.com");
    }

    @Test
    @Order(4)
    void volunteer_reads_categories_for_active_edition() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories").session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> categories = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertThat(categories).hasSize(2);
        assertThat(categories).extracting(EditionCategoryDto::name).containsExactly("Jouets", "Livres");
    }

    @Test
    @Order(5)
    void first_item_for_seller_in_category_gets_least_loaded_table() throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerAId, jouetsCategoryId, "Kapla", new BigDecimal("5.00"), false, null);
        MvcResult result = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        ItemDto created = objectMapper.readValue(result.getResponse().getContentAsString(), ItemDto.class);
        itemKaplaId = created.id();
        assertThat(created.tableNumber()).isEqualTo(1);
        assertThat(created.categoryName()).isEqualTo("Jouets");
        assertThat(created.price()).isEqualByComparingTo("5.00");
    }

    @Test
    @Order(6)
    void second_item_same_seller_same_category_reuses_table() throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerAId, jouetsCategoryId, "Puzzle", new BigDecimal("3.50"), false, null);
        MvcResult result = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        ItemDto created = objectMapper.readValue(result.getResponse().getContentAsString(), ItemDto.class);
        itemPuzzleId = created.id();
        assertThat(created.tableNumber()).isEqualTo(1);
    }

    @Test
    @Order(7)
    void first_item_other_seller_same_category_picks_least_loaded_table() throws Exception {
        // Table 1 already carries 2 items (seller A) vs table 2 empty -> table 2 is picked.
        CreateItemDto payload = new CreateItemDto(sellerBId, jouetsCategoryId, "Velo", new BigDecimal("20.00"), false, null);
        MvcResult result = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        ItemDto created = objectMapper.readValue(result.getResponse().getContentAsString(), ItemDto.class);
        itemVeloId = created.id();
        assertThat(created.tableNumber()).isEqualTo(2);
    }

    @Test
    @Order(8)
    void first_item_other_category_considers_load_across_all_categories() throws Exception {
        // Livres tables are [2,3]; table 2 already has 1 item (seller B, Jouets) so table 3 is picked
        // even though table counting is done across categories, not just within Livres.
        CreateItemDto payload = new CreateItemDto(sellerCId, livresCategoryId, "BD Tintin", new BigDecimal("8.00"), false, null);
        MvcResult result = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        ItemDto created = objectMapper.readValue(result.getResponse().getContentAsString(), ItemDto.class);
        itemBdId = created.id();
        assertThat(created.tableNumber()).isEqualTo(3);
    }

    @Test
    @Order(9)
    void get_items_by_seller_returns_list_ordered_by_name() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<ItemDto> items = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertThat(items).extracting(ItemDto::name).containsExactly("Kapla", "Puzzle");
    }

    @Test
    @Order(10)
    void toggle_incomplete_and_comment_in_deposit_phase() throws Exception {
        ItemCompletenessDto payload = new ItemCompletenessDto(true, "Piece manquante");
        mockMvc.perform(patch("/api/items/" + itemPuzzleId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomplete").value(true))
                .andExpect(jsonPath("$.comment").value("Piece manquante"));
    }

    @Test
    @Order(11)
    void update_name_and_price_without_category_change_keeps_table() throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerAId, jouetsCategoryId, "Puzzle 500 pieces", new BigDecimal("4.00"), true, "Piece manquante");
        mockMvc.perform(put("/api/items/" + itemPuzzleId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Puzzle 500 pieces"))
                .andExpect(jsonPath("$.price").value(4.00))
                .andExpect(jsonPath("$.tableNumber").value(1));
    }

    @Test
    @Order(12)
    void update_category_reassigns_table() throws Exception {
        // Excluding item Kapla itself: table 1 keeps 1 item (Puzzle), table 2 has 1 item (Velo),
        // table 3 has 1 item (BD Tintin). Livres tables are [2,3]; table 2 and 3 tie at 1 each,
        // so the smallest (2) is picked -> Kapla moves from table 1 to table 2.
        CreateItemDto payload = new CreateItemDto(sellerAId, livresCategoryId, "Kapla", new BigDecimal("5.00"), false, null);
        mockMvc.perform(put("/api/items/" + itemKaplaId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Livres"))
                .andExpect(jsonPath("$.tableNumber").value(2));
    }

    @Test
    @Order(13)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(14)
    void update_item_outside_deposit_phase_is_blocked() throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerAId, jouetsCategoryId, "Puzzle", new BigDecimal("4.00"), false, null);
        mockMvc.perform(put("/api/items/" + itemPuzzleId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }

    @Test
    @Order(15)
    void delete_item_outside_deposit_phase_is_blocked() throws Exception {
        mockMvc.perform(delete("/api/items/" + itemPuzzleId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-modification-locked")));
    }

    @Test
    @Order(16)
    void toggle_incomplete_still_allowed_outside_deposit_phase() throws Exception {
        ItemCompletenessDto payload = new ItemCompletenessDto(false, "OK");
        mockMvc.perform(patch("/api/items/" + itemPuzzleId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomplete").value(false))
                .andExpect(jsonPath("$.comment").value("OK"));
    }

    @Test
    @Order(17)
    void seller_role_cannot_create_or_read_items() throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerAId, jouetsCategoryId, "Contrebande", new BigDecimal("1.00"), false, null);
        mockMvc.perform(post("/api/items")
                        .session(sellerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/items").param("sellerProfileId", String.valueOf(sellerAId)).session(sellerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(18)
    void rollback_edition_to_deposit_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(19)
    void third_item_same_seller_same_category_does_not_crash_table_lookup() throws Exception {
        // Regression test: with 2 pre-existing items already sharing the same seller+category
        // table, ItemRepository.findTableNumberBySellerProfileIdAndCategoryId used to throw
        // NonUniqueResultException on the 3rd item (fixed by adding DISTINCT to that query).
        // Uses a dedicated seller so it doesn't perturb the table-load counts other tests rely on.
        Long sellerDId = createSeller("Diane", "Testeuse", "diane.testeuse@email.com");
        CreateItemDto first = new CreateItemDto(sellerDId, jouetsCategoryId, "Lego", new BigDecimal("10.00"), false, null);
        MvcResult firstResult = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated())
                .andReturn();
        int assignedTable = objectMapper.readValue(firstResult.getResponse().getContentAsString(), ItemDto.class).tableNumber();

        CreateItemDto second = new CreateItemDto(sellerDId, jouetsCategoryId, "Playmobil", new BigDecimal("7.00"), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value(assignedTable));

        CreateItemDto third = new CreateItemDto(sellerDId, jouetsCategoryId, "Duplo", new BigDecimal("6.00"), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(third)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value(assignedTable));
    }

    @Test
    @Order(20)
    void delete_item_in_deposit_phase() throws Exception {
        mockMvc.perform(delete("/api/items/" + itemVeloId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(21)
    void delete_seller_with_remaining_items_is_blocked() throws Exception {
        mockMvc.perform(delete("/api/admin/sellers/" + sellerAId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/seller-deletion-locked")));
    }

    @Test
    @Order(22)
    void delete_seller_without_remaining_items_succeeds() throws Exception {
        // Seller B's only item (Velo) was deleted in the previous step.
        mockMvc.perform(delete("/api/admin/sellers/" + sellerBId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(23)
    void rollback_edition_to_preparation_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    @Test
    @Order(24)
    void delete_edition_with_remaining_items_is_blocked() throws Exception {
        mockMvc.perform(delete("/api/admin/editions/" + editionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/edition-cannot-be-deleted")));
    }

    @Test
    @Order(25)
    void delete_unknown_item_returns_404() throws Exception {
        mockMvc.perform(delete("/api/items/999999")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/item-not-found")));
    }

    private Long createSeller(String firstName, String lastName, String email) throws Exception {
        SellerDto payload = new SellerDto(null, firstName, lastName, email, "0600000000");
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SellerDto.class).id();
    }
}
